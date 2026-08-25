package com.deployforge.workspace;

import com.deployforge.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/** Membership of a user in a workspace, carrying the role that drives every authorization check. */
@Entity
@Table(
        name = "workspace_members",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "ux_workspace_member",
                        columnNames = {"workspace_id", "user_id"}))
public class WorkspaceMember extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private WorkspaceRole role;

    protected WorkspaceMember() {}

    public WorkspaceMember(UUID workspaceId, UUID userId, WorkspaceRole role) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public WorkspaceRole getRole() {
        return role;
    }

    public void setRole(WorkspaceRole role) {
        this.role = role;
    }
}
