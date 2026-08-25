package com.deployforge.log;

import com.deployforge.common.api.CursorPageResponse;
import com.deployforge.deployment.event.DeploymentEvent;
import com.deployforge.deployment.event.DeploymentEventPublisher;
import com.deployforge.security.SecretRedactionService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes, streams and reads deployment logs.
 *
 * <p>Three things happen on every append, in this order:
 *
 * <ol>
 *   <li>the message is passed through {@link SecretRedactionService}, using both heuristics and the
 *       literal secret values registered for that deployment, so a leaked token never reaches storage;
 *   <li>a per-deployment sequence number is allocated (Redis {@code INCR}, with a database fallback)
 *       giving clients a stable pagination cursor;
 *   <li>the line is persisted and pushed to subscribed WebSocket clients.
 * </ol>
 *
 * <p>Reads are cursor paginated. Log volume per deployment can be large, so nothing here ever loads a
 * whole deployment's logs unbounded.
 */
@Service
public class DeploymentLogService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentLogService.class);

    private static final String SEQUENCE_KEY_PREFIX = "df:logseq:";
    private static final int MAX_MESSAGE_LENGTH = 8000;
    private static final int MAX_PAGE_SIZE = 500;

    private final DeploymentLogRepository repository;
    private final SecretRedactionService redaction;
    private final StringRedisTemplate redis;
    private final ObjectProvider<DeploymentEventPublisher> eventPublisher;

    /**
     * Literal secret values for in-flight deployments, registered by the pipeline for the duration of
     * a build. Kept in memory only, keyed by deployment id, and dropped when the deployment finishes.
     */
    private final Map<UUID, Set<String>> secretScopes = new ConcurrentHashMap<>();

    public DeploymentLogService(
            DeploymentLogRepository repository,
            SecretRedactionService redaction,
            StringRedisTemplate redis,
            ObjectProvider<DeploymentEventPublisher> eventPublisher) {
        this.repository = repository;
        this.redaction = redaction;
        this.redis = redis;
        this.eventPublisher = eventPublisher;
    }

    // ------------------------------------------------------------------ secret scope

    /** Registers the decrypted values that must never appear in this deployment's logs. */
    public void registerSecrets(UUID deploymentId, Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        secretScopes.put(deploymentId, new LinkedHashSet<>(values));
    }

    public void clearSecrets(UUID deploymentId) {
        secretScopes.remove(deploymentId);
    }

    // ------------------------------------------------------------------ writes

    @Transactional
    public DeploymentLog append(UUID deploymentId, LogLevel level, LogSource source, String message) {
        String safeMessage = sanitize(deploymentId, message);
        long sequence = nextSequence(deploymentId);
        Instant now = Instant.now();

        DeploymentLog entry =
                repository.save(new DeploymentLog(deploymentId, now, level, source, safeMessage, sequence));

        publish(new DeploymentEvent.Log(deploymentId, now, sequence, level, source, safeMessage));
        return entry;
    }

    /** Convenience for the pipeline's own narration. */
    public void system(UUID deploymentId, String message) {
        append(deploymentId, LogLevel.INFO, LogSource.SYSTEM, message);
    }

    public void error(UUID deploymentId, LogSource source, String message) {
        append(deploymentId, LogLevel.ERROR, source, message);
    }

    /**
     * Appends several lines in one transaction.
     *
     * <p>Used when draining buffered process output: one round trip instead of one per line.
     */
    @Transactional
    public void appendBatch(UUID deploymentId, LogSource source, List<Line> lines) {
        if (lines.isEmpty()) {
            return;
        }
        List<DeploymentLog> entries = new ArrayList<>(lines.size());
        List<DeploymentEvent> events = new ArrayList<>(lines.size());
        for (Line line : lines) {
            String safeMessage = sanitize(deploymentId, line.message());
            long sequence = nextSequence(deploymentId);
            Instant now = Instant.now();
            entries.add(
                    new DeploymentLog(deploymentId, now, line.level(), source, safeMessage, sequence));
            events.add(
                    new DeploymentEvent.Log(deploymentId, now, sequence, line.level(), source, safeMessage));
        }
        repository.saveAll(entries);
        events.forEach(this::publish);
    }

    // ------------------------------------------------------------------ reads

    /**
     * Cursor paginated logs.
     *
     * @param afterSequence exclusive lower bound; {@code null} or 0 starts from the beginning
     * @param level optional minimum level filter
     * @param source optional source filter
     */
    @Transactional(readOnly = true)
    public CursorPageResponse<LogEntryResponse> read(
            UUID deploymentId, Long afterSequence, int limit, LogLevel level, LogSource source) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        long cursor = afterSequence == null ? 0L : Math.max(afterSequence, 0L);

        // Fetch one extra row to know whether more data exists without a second count query.
        List<DeploymentLog> rows =
                repository
                        .findByDeploymentIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
                                deploymentId, cursor, PageRequest.of(0, safeLimit + 1));

        List<DeploymentLog> filtered =
                rows.stream()
                        .filter(row -> level == null || row.getLevel().atLeast(level))
                        .filter(row -> source == null || row.getSource() == source)
                        .toList();

        boolean hasMore = rows.size() > safeLimit;
        List<DeploymentLog> page =
                filtered.size() > safeLimit ? filtered.subList(0, safeLimit) : filtered;

        Long nextCursor =
                page.isEmpty()
                        ? (rows.isEmpty() ? null : rows.get(rows.size() - 1).getSequenceNumber())
                        : page.get(page.size() - 1).getSequenceNumber();

        return CursorPageResponse.of(
                page.stream().map(LogEntryResponse::from).toList(), nextCursor, hasMore, safeLimit);
    }

    /** Last {@code limit} lines, oldest first. Used to prime a newly connected log viewer. */
    @Transactional(readOnly = true)
    public List<LogEntryResponse> tail(UUID deploymentId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        List<DeploymentLog> rows =
                repository.findByDeploymentIdOrderBySequenceNumberDesc(
                        deploymentId, PageRequest.of(0, safeLimit));
        return rows.stream()
                .sorted(Comparator.comparingLong(DeploymentLog::getSequenceNumber))
                .map(LogEntryResponse::from)
                .toList();
    }

    /** Raw tail used as AI evidence. Already redacted, since redaction happens on write. */
    @Transactional(readOnly = true)
    public List<String> tailPlain(UUID deploymentId, int limit) {
        return tail(deploymentId, limit).stream()
                .map(entry -> entry.source() + " " + entry.level() + " " + entry.message())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> errorLines(UUID deploymentId, int limit) {
        return repository
                .findByDeploymentIdAndLevelOrderBySequenceNumberDesc(
                        deploymentId, LogLevel.ERROR, PageRequest.of(0, Math.min(limit, MAX_PAGE_SIZE)))
                .stream()
                .sorted(Comparator.comparingLong(DeploymentLog::getSequenceNumber))
                .map(DeploymentLog::getMessage)
                .toList();
    }

    @Transactional(readOnly = true)
    public long count(UUID deploymentId) {
        return repository.countByDeploymentId(deploymentId);
    }

    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        return repository.deleteOlderThan(cutoff);
    }

    // ------------------------------------------------------------------ internals

    private String sanitize(UUID deploymentId, String message) {
        String value = message == null ? "" : message;
        // Strip control characters and ANSI escapes; build tools emit plenty and they break the viewer.
        value = value.replaceAll("\u001B\\[[;?0-9]*[a-zA-Z]", "");
        value = value.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");
        if (value.length() > MAX_MESSAGE_LENGTH) {
            value = value.substring(0, MAX_MESSAGE_LENGTH) + " ...[truncated]";
        }
        Set<String> secrets = secretScopes.getOrDefault(deploymentId, Set.of());
        return redaction.redact(value, secrets);
    }

    /**
     * Allocates the next sequence number.
     *
     * <p>Redis is the fast path. If Redis is unavailable the database high-water mark is used instead -
     * slower, but a deployment must not fail because the log counter is unreachable.
     */
    private long nextSequence(UUID deploymentId) {
        String key = SEQUENCE_KEY_PREFIX + deploymentId;
        try {
            Long value = redis.opsForValue().increment(key);
            if (value != null) {
                if (value == 1L) {
                    // First line of this deployment: align with any pre-existing rows and expire the counter.
                    long existing = repository.findMaxSequence(deploymentId);
                    if (existing > 0) {
                        redis.opsForValue().set(key, String.valueOf(existing + 1));
                        redis.expire(key, java.time.Duration.ofDays(2));
                        return existing + 1;
                    }
                    redis.expire(key, java.time.Duration.ofDays(2));
                }
                return value;
            }
        } catch (DataAccessException e) {
            log.debug("log_sequence_redis_unavailable deployment={} reason={}", deploymentId, e.getMessage());
        }
        return repository.findMaxSequence(deploymentId) + 1;
    }

    private void publish(DeploymentEvent event) {
        DeploymentEventPublisher publisher = eventPublisher.getIfAvailable();
        if (publisher == null) {
            return;
        }
        try {
            publisher.publish(event);
        } catch (RuntimeException e) {
            // Streaming is a convenience; a broken socket must never fail a deployment.
            log.debug("log_publish_failed deployment={} reason={}", event.deploymentId(), e.getMessage());
        }
    }

    /** A line to append, used by {@link #appendBatch}. */
    public record Line(LogLevel level, String message) {
        public static Line info(String message) {
            return new Line(LogLevel.INFO, message);
        }

        public static Line error(String message) {
            return new Line(LogLevel.ERROR, message);
        }
    }

    public record LogEntryResponse(
            long sequence, Instant timestamp, LogLevel level, LogSource source, String message) {

        static LogEntryResponse from(DeploymentLog entry) {
            return new LogEntryResponse(
                    entry.getSequenceNumber(),
                    entry.getLoggedAt(),
                    entry.getLevel(),
                    entry.getSource(),
                    entry.getMessage());
        }
    }
}
