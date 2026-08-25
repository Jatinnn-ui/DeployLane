package com.deployforge.activity;

import com.deployforge.activity.ActivityService.ActivityResponse;
import com.deployforge.common.api.PageResponse;
import com.deployforge.project.ProjectAccessGuard;
import com.deployforge.security.AuthenticatedUser;
import com.deployforge.workspace.AuthorizationService;
import com.deployforge.workspace.WorkspaceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Activity feed, scoped to a workspace, a project, or everything the caller can see. */
@RestController
@Validated
@Tag(name = "Activity", description = "Who did what, across workspaces and projects")
public class ActivityController {

    private final ActivityService activityService;
    private final AuthorizationService authorization;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectAccessGuard accessGuard;

    public ActivityController(
            ActivityService activityService,
            AuthorizationService authorization,
            WorkspaceRepository workspaceRepository,
            ProjectAccessGuard accessGuard) {
        this.activityService = activityService;
        this.authorization = authorization;
        this.workspaceRepository = workspaceRepository;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/api/v1/activity")
    @Operation(summary = "Activity across every workspace the caller belongs to")
    public PageResponse<ActivityResponse> feed(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "30") @Min(1) @Max(100) int size) {
        List<UUID> workspaceIds =
                workspaceRepository.findAllForMember(user.userId()).stream()
                        .map(workspace -> workspace.getId())
                        .toList();
        return activityService.forWorkspaces(workspaceIds, page, size);
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/activity")
    public PageResponse<ActivityResponse> workspaceFeed(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "30") @Min(1) @Max(100) int size) {
        authorization.requireMembership(workspaceId, user.userId());
        return activityService.forWorkspace(workspaceId, page, size);
    }

    @GetMapping("/api/v1/projects/{projectId}/activity")
    public PageResponse<ActivityResponse> projectFeed(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "30") @Min(1) @Max(100) int size) {
        accessGuard.requireVisible(projectId, user.userId());
        return activityService.forProject(projectId, page, size);
    }
}
