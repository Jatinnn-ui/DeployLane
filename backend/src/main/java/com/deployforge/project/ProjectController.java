package com.deployforge.project;

import com.deployforge.common.api.PageResponse;
import com.deployforge.project.dto.ProjectDtos.ImportProjectRequest;
import com.deployforge.project.dto.ProjectDtos.ProjectResponse;
import com.deployforge.project.dto.ProjectDtos.ProjectSummaryResponse;
import com.deployforge.project.dto.ProjectDtos.UpdateProjectRequest;
import com.deployforge.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
@Validated
@Tag(name = "Projects", description = "Import and configure deployable applications")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectImportService importService;

    public ProjectController(ProjectService projectService, ProjectImportService importService) {
        this.projectService = projectService;
        this.importService = importService;
    }

    @GetMapping
    @Operation(
            summary = "List projects",
            description =
                    "Scoped to one workspace when workspaceId is supplied, otherwise every project the caller "
                            + "can see. Each item carries a snapshot of its latest deployment.")
    public PageResponse<ProjectSummaryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(name = "workspaceId", required = false) UUID workspaceId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return projectService.list(user.userId(), workspaceId, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Import a GitHub repository",
            description =
                    "Verifies repository and branch access, detects the framework, creates the project and its "
                            + "production environment, and optionally seeds environment variables.")
    public ProjectResponse importProject(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ImportProjectRequest request) {
        return importService.importProject(user.userId(), request);
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return projectService.get(projectId, user.userId());
    }

    @PatchMapping("/{projectId}")
    @Operation(summary = "Update project settings", description = "Requires ADMIN or OWNER")
    public ProjectResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        return projectService.update(projectId, user.userId(), request);
    }

    @DeleteMapping("/{projectId}")
    @Operation(
            summary = "Delete a project",
            description =
                    "Stops and removes every container, image and build directory owned by the project before "
                            + "deleting its data. Irreversible.")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        projectService.delete(projectId, user.userId());
        return ResponseEntity.noContent().build();
    }
}
