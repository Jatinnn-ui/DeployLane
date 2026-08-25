package com.deployforge.workspace.dto;

import com.deployforge.user.UserResponse;
import com.deployforge.workspace.Permission;
import com.deployforge.workspace.WorkspaceRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Request and response payloads for the workspace API. */
public final class WorkspaceDtos {

    private WorkspaceDtos() {}

    public record WorkspaceResponse(
            UUID id,
            String name,
            String slug,
            UUID ownerId,
            WorkspaceRole role,
            Set<Permission> permissions,
            long memberCount,
            Instant createdAt) {}

    public record WorkspaceMemberResponse(
            UUID id, UserResponse user, WorkspaceRole role, Instant joinedAt) {}

    public record CreateWorkspaceRequest(
            @NotBlank @Size(min = 2, max = 120) String name,
            @Size(max = 120)
                    @Pattern(
                            regexp = "^$|^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$",
                            message = "must be lowercase alphanumeric with single hyphens")
                    String slug) {}

    public record UpdateWorkspaceRequest(@NotBlank @Size(min = 2, max = 120) String name) {}

    public record AddMemberRequest(
            @NotBlank @Size(max = 100) String githubUsername, @NotNull WorkspaceRole role) {}

    public record UpdateMemberRoleRequest(@NotNull WorkspaceRole role) {}

    public record MemberListResponse(List<WorkspaceMemberResponse> members) {}
}
