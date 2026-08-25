package com.deployforge.workspace;

import com.deployforge.security.AuthenticatedUser;
import com.deployforge.workspace.dto.WorkspaceDtos.AddMemberRequest;
import com.deployforge.workspace.dto.WorkspaceDtos.CreateWorkspaceRequest;
import com.deployforge.workspace.dto.WorkspaceDtos.UpdateMemberRoleRequest;
import com.deployforge.workspace.dto.WorkspaceDtos.UpdateWorkspaceRequest;
import com.deployforge.workspace.dto.WorkspaceDtos.WorkspaceMemberResponse;
import com.deployforge.workspace.dto.WorkspaceDtos.WorkspaceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
@Tag(name = "Workspaces", description = "Tenants that own projects, members and deployments")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    @Operation(summary = "List workspaces the caller belongs to, including their role")
    public List<WorkspaceResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return workspaceService.listForUser(user.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a workspace; the caller becomes its OWNER")
    public WorkspaceResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateWorkspaceRequest request) {
        return workspaceService.create(user.userId(), request);
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse get(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID workspaceId) {
        return workspaceService.get(workspaceId, user.userId());
    }

    @GetMapping("/by-slug/{slug}")
    public WorkspaceResponse getBySlug(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String slug) {
        return workspaceService.getBySlug(slug, user.userId());
    }

    @PatchMapping("/{workspaceId}")
    @Operation(summary = "Rename a workspace", description = "Requires the OWNER role")
    public WorkspaceResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateWorkspaceRequest request) {
        return workspaceService.update(workspaceId, user.userId(), request);
    }

    @GetMapping("/{workspaceId}/members")
    public List<WorkspaceMemberResponse> members(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID workspaceId) {
        return workspaceService.listMembers(workspaceId, user.userId());
    }

    @PostMapping("/{workspaceId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Add an existing DeployLane user to the workspace",
            description = "The invitee must have signed in with GitHub at least once")
    public WorkspaceMemberResponse addMember(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody AddMemberRequest request) {
        return workspaceService.addMember(workspaceId, user.userId(), request);
    }

    @PatchMapping("/{workspaceId}/members/{memberId}")
    public WorkspaceMemberResponse updateMemberRole(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        return workspaceService.updateMemberRole(workspaceId, memberId, user.userId(), request.role());
    }

    @DeleteMapping("/{workspaceId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberId) {
        workspaceService.removeMember(workspaceId, memberId, user.userId());
        return ResponseEntity.noContent().build();
    }
}
