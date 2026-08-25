package com.deployforge.activity;

import com.deployforge.common.api.PageResponse;
import com.deployforge.common.util.JsonCodec;
import com.deployforge.security.SecretRedactionService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records and reads the activity feed.
 *
 * <p>Writes are best effort: a failure to record an event must never fail the operation that caused
 * it. Metadata is passed through secret redaction before it is stored, because callers pass values
 * such as branch names and commit messages that occasionally contain tokens.
 */
@Service
@Transactional(readOnly = true)
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    private final ActivityRepository repository;
    private final JsonCodec json;
    private final SecretRedactionService redaction;

    public ActivityService(
            ActivityRepository repository, JsonCodec json, SecretRedactionService redaction) {
        this.repository = repository;
        this.json = json;
        this.redaction = redaction;
    }

    @Transactional
    public void record(
            UUID workspaceId,
            UUID projectId,
            UUID actorId,
            String actorName,
            ActivityAction action,
            String resourceType,
            String resourceId,
            Map<String, Object> metadata) {
        try {
            String encodedMetadata =
                    metadata == null || metadata.isEmpty() ? null : redaction.redact(json.write(metadata));
            repository.save(
                    new ActivityEvent(
                            workspaceId,
                            projectId,
                            actorId,
                            actorName,
                            action.name(),
                            resourceType,
                            resourceId,
                            encodedMetadata));
        } catch (RuntimeException e) {
            log.warn("activity_record_failed action={} reason={}", action, e.getMessage());
        }
    }

    public PageResponse<ActivityResponse> forWorkspace(UUID workspaceId, int page, int size) {
        return PageResponse.from(
                repository.findByWorkspaceIdOrderByCreatedAtDesc(
                        workspaceId, PageRequest.of(page, size)),
                this::toResponse);
    }

    public PageResponse<ActivityResponse> forWorkspaces(List<UUID> workspaceIds, int page, int size) {
        if (workspaceIds.isEmpty()) {
            return PageResponse.of(List.of());
        }
        return PageResponse.from(
                repository.findForWorkspaces(workspaceIds, PageRequest.of(page, size)), this::toResponse);
    }

    public PageResponse<ActivityResponse> forProject(UUID projectId, int page, int size) {
        return PageResponse.from(
                repository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(page, size)),
                this::toResponse);
    }

    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        return repository.deleteOlderThan(cutoff);
    }

    private ActivityResponse toResponse(ActivityEvent event) {
        return new ActivityResponse(
                event.getId(),
                event.getWorkspaceId(),
                event.getProjectId(),
                event.getActorId(),
                event.getActorName(),
                event.getAction(),
                event.getResourceType(),
                event.getResourceId(),
                json.readMap(event.getMetadata()),
                event.getCreatedAt());
    }

    public record ActivityResponse(
            UUID id,
            UUID workspaceId,
            UUID projectId,
            UUID actorId,
            String actorName,
            String action,
            String resourceType,
            String resourceId,
            Map<String, Object> metadata,
            Instant createdAt) {}
}
