package com.deployforge.activity;

import com.deployforge.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Append-only record of who did what.
 *
 * <p>Doubles as the activity feed and as the foundation for a future audit log: actor, action,
 * resource, resource id, metadata and timestamp are exactly the fields an audit trail needs. It is
 * never updated or deleted by application code.
 *
 * <p>{@code metadata} holds a small JSON object. Callers are responsible for keeping secrets out of
 * it - the feed is visible to every workspace member.
 */
@Entity
@Table(name = "activity_events")
public class ActivityEvent extends BaseEntity {

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType;

    @Column(name = "resource_id", length = 128)
    private String resourceId;

    @Column(name = "metadata", columnDefinition = "text")
    private String metadata;

    protected ActivityEvent() {}

    public ActivityEvent(
            UUID workspaceId,
            UUID projectId,
            UUID actorId,
            String actorName,
            String action,
            String resourceType,
            String resourceId,
            String metadata) {
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.actorId = actorId;
        this.actorName = actorName;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.metadata = metadata;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getMetadata() {
        return metadata;
    }
}
