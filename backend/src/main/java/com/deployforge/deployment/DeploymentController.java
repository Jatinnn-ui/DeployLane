package com.deployforge.deployment;

import com.deployforge.common.api.PageResponse;
import com.deployforge.deployment.dto.DeploymentDtos.CreateDeploymentRequest;
import com.deployforge.deployment.dto.DeploymentDtos.DeploymentResponse;
import com.deployforge.deployment.dto.DeploymentDtos.DeploymentSummaryResponse;
import com.deployforge.deployment.dto.DeploymentDtos.QueueStatusResponse;
import com.deployforge.deployment.dto.DeploymentDtos.RollbackCandidate;
import com.deployforge.deployment.dto.DeploymentDtos.StepResponse;
import com.deployforge.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deployment lifecycle endpoints.
 *
 * <p>{@code POST} returns as soon as the deployment is queued - it never waits for the build. Clients
 * follow progress over {@code /ws/deployments/{id}} or by polling the detail endpoint.
 */
@RestController
@Validated
@Tag(name = "Deployments", description = "Create, inspect, cancel, redeploy and roll back deployments")
public class DeploymentController {

    private final DeploymentService deploymentService;

    public DeploymentController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @PostMapping("/api/v1/projects/{projectId}/deployments")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Deploy a project",
            description =
                    "Creates a QUEUED deployment and returns immediately. Requires the DEPLOY permission. "
                            + "Rate limited per user.")
    public DeploymentResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId,
            @Valid @RequestBody(required = false) CreateDeploymentRequest request) {
        return deploymentService.create(
                projectId,
                user.userId(),
                request == null ? new CreateDeploymentRequest(null, null, null) : request);
    }

    @GetMapping("/api/v1/projects/{projectId}/deployments")
    @Operation(summary = "Deployment history, newest first")
    public PageResponse<DeploymentSummaryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId,
            @RequestParam(name = "environmentId", required = false) UUID environmentId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return deploymentService.list(projectId, user.userId(), environmentId, page, size);
    }

    @GetMapping("/api/v1/deployments/{deploymentId}")
    public DeploymentResponse get(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID deploymentId) {
        return deploymentService.get(deploymentId, user.userId());
    }

    @GetMapping("/api/v1/deployments/{deploymentId}/steps")
    @Operation(summary = "Timeline of the deployment's pipeline steps")
    public List<StepResponse> steps(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID deploymentId) {
        return deploymentService.steps(deploymentId, user.userId());
    }

    @PostMapping("/api/v1/deployments/{deploymentId}/cancel")
    @Operation(
            summary = "Cancel an in-flight deployment",
            description =
                    "Allowed while QUEUED, CLONING, DETECTING, BUILDING, IMAGE_BUILDING, STARTING or "
                            + "HEALTH_CHECKING. Partial resources are released; the currently live deployment is untouched.")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID deploymentId) {
        deploymentService.cancel(deploymentId, user.userId());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/api/v1/deployments/{deploymentId}/redeploy")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Rebuild the same commit",
            description = "Creates a new deployment with triggerType=REDEPLOY. History is never rewritten.")
    public DeploymentResponse redeploy(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID deploymentId) {
        return deploymentService.redeploy(deploymentId, user.userId());
    }

    @PostMapping("/api/v1/deployments/{deploymentId}/rollback")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Roll back to this deployment",
            description =
                    "Creates a new deployment with triggerType=ROLLBACK that reuses this deployment's image. "
                            + "Requires ADMIN or OWNER, and the target must have reached READY with its image still present.")
    public DeploymentResponse rollback(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID deploymentId) {
        return deploymentService.rollback(deploymentId, user.userId());
    }

    @GetMapping("/api/v1/deployments/{deploymentId}/rollback-candidates")
    @Operation(summary = "Earlier successful deployments that can still be rolled back to")
    public List<RollbackCandidate> rollbackCandidates(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID deploymentId) {
        return deploymentService.rollbackCandidates(deploymentId, user.userId());
    }

    @GetMapping("/api/v1/deployments/queue-status")
    @Operation(summary = "Queue depth and worker concurrency")
    public QueueStatusResponse queueStatus(@AuthenticationPrincipal AuthenticatedUser user) {
        return deploymentService.queueStatus();
    }
}
