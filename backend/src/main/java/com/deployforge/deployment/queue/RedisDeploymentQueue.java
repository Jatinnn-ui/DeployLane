package com.deployforge.deployment.queue;

import com.deployforge.common.error.ErrorCode;
import com.deployforge.common.error.Exceptions.ExternalServiceException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis backed implementation of {@link DeploymentQueue}.
 *
 * <p>Design choices worth stating:
 *
 * <ul>
 *   <li><b>List + blocking pop.</b> {@code LPUSH} / {@code BRPOP} gives FIFO and lets workers block
 *       instead of polling, so a queued deployment starts within milliseconds.
 *   <li><b>Enqueue must not silently fail.</b> If Redis is unreachable the API rejects the deployment up
 *       front rather than accepting work nobody will ever pick up.
 *   <li><b>Environment lock via SET NX EX.</b> A TTL means a crashed worker cannot deadlock an
 *       environment forever, and release is guarded by holder identity so a worker never frees someone
 *       else's lock.
 * </ul>
 */
@Component
public class RedisDeploymentQueue implements DeploymentQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisDeploymentQueue.class);

    private static final String QUEUE_KEY = "df:queue:deployments";
    private static final String CANCEL_PREFIX = "df:cancel:";
    private static final String ENV_LOCK_PREFIX = "df:envlock:";
    private static final Duration CANCEL_TTL = Duration.ofHours(6);

    private final StringRedisTemplate redis;

    public RedisDeploymentQueue(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void enqueue(UUID deploymentId) {
        try {
            redis.opsForList().leftPush(QUEUE_KEY, deploymentId.toString());
            log.debug("deployment_enqueued deployment={} depth={}", deploymentId, depth());
        } catch (DataAccessException e) {
            log.error("deployment_enqueue_failed deployment={} reason={}", deploymentId, e.getMessage());
            throw new ExternalServiceException(
                    ErrorCode.RUNTIME_UNAVAILABLE,
                    "Deployments cannot be queued because Redis is unavailable. Check platform health and retry.",
                    e);
        }
    }

    @Override
    public Optional<UUID> poll(Duration timeout) {
        try {
            String value = redis.opsForList().rightPop(QUEUE_KEY, timeout);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            log.warn("deployment_queue_bad_entry value_skipped");
            return Optional.empty();
        } catch (DataAccessException e) {
            log.debug("deployment_queue_poll_failed reason={}", e.getMessage());
            sleepQuietly(Duration.ofSeconds(2));
            return Optional.empty();
        }
    }

    @Override
    public void requeue(UUID deploymentId) {
        try {
            redis.opsForList().leftPush(QUEUE_KEY, deploymentId.toString());
        } catch (DataAccessException e) {
            log.warn("deployment_requeue_failed deployment={} reason={}", deploymentId, e.getMessage());
        }
    }

    @Override
    public long depth() {
        try {
            Long size = redis.opsForList().size(QUEUE_KEY);
            return size == null ? 0L : size;
        } catch (DataAccessException e) {
            return 0L;
        }
    }

    @Override
    public void requestCancellation(UUID deploymentId) {
        try {
            redis.opsForValue().set(CANCEL_PREFIX + deploymentId, "1", CANCEL_TTL);
        } catch (DataAccessException e) {
            log.warn("cancellation_flag_failed deployment={} reason={}", deploymentId, e.getMessage());
        }
    }

    @Override
    public boolean isCancellationRequested(UUID deploymentId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(CANCEL_PREFIX + deploymentId));
        } catch (DataAccessException e) {
            return false;
        }
    }

    @Override
    public void clearCancellation(UUID deploymentId) {
        try {
            redis.delete(CANCEL_PREFIX + deploymentId);
        } catch (DataAccessException e) {
            log.debug("cancellation_clear_failed deployment={} reason={}", deploymentId, e.getMessage());
        }
    }

    @Override
    public boolean tryAcquireEnvironmentLock(UUID environmentId, UUID deploymentId, Duration ttl) {
        try {
            Boolean acquired =
                    redis
                            .opsForValue()
                            .setIfAbsent(ENV_LOCK_PREFIX + environmentId, deploymentId.toString(), ttl);
            return Boolean.TRUE.equals(acquired);
        } catch (DataAccessException e) {
            // Without Redis we cannot guarantee exclusivity, so refuse rather than risk two
            // deployments fighting over the same environment.
            log.warn("environment_lock_unavailable environment={} reason={}", environmentId, e.getMessage());
            return false;
        }
    }

    @Override
    public void releaseEnvironmentLock(UUID environmentId, UUID deploymentId) {
        try {
            String key = ENV_LOCK_PREFIX + environmentId;
            String holder = redis.opsForValue().get(key);
            if (holder != null && holder.equals(deploymentId.toString())) {
                redis.delete(key);
            }
        } catch (DataAccessException e) {
            log.debug("environment_unlock_failed environment={} reason={}", environmentId, e.getMessage());
        }
    }

    @Override
    public Optional<UUID> environmentLockHolder(UUID environmentId) {
        try {
            String holder = redis.opsForValue().get(ENV_LOCK_PREFIX + environmentId);
            return holder == null ? Optional.empty() : Optional.of(UUID.fromString(holder));
        } catch (DataAccessException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
