package com.deployforge.notification;

import com.deployforge.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/** An in-app notification for a single user. Never contains secret values. */
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 48)
    private NotificationType type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message", length = 2000)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "deployment_id")
    private UUID deploymentId;

    protected Notification() {}

    public Notification(
            UUID userId,
            NotificationType type,
            String title,
            String message,
            UUID projectId,
            UUID deploymentId) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message == null || message.length() <= 2000 ? message : message.substring(0, 2000);
        this.projectId = projectId;
        this.deploymentId = deploymentId;
        this.read = false;
    }

    public UUID getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return read;
    }

    public void markRead() {
        this.read = true;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getDeploymentId() {
        return deploymentId;
    }
}
