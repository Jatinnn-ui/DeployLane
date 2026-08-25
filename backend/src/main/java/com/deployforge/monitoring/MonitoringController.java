package com.deployforge.monitoring;

import com.deployforge.deployment.Deployment;
import com.deployforge.deployment.DeploymentService;
import com.deployforge.monitoring.dto.MonitoringDtos.DeploymentMetricsResponse;
import com.deployforge.monitoring.dto.MonitoringDtos.ProjectHealthResponse;
import com.deployforge.project.ProjectAccessGuard;
import com.deployforge.security.AuthenticatedUser;
import com.deployforge.workspace.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Health and resource usage of running deployments. */
@RestController
@Validated
@Tag(name = "Monitoring", description = "Container health, CPU, memory, uptime and restarts")
public class MonitoringController {

    private final MonitoringService monitoringService;
    private final DeploymentService deploymentService;
    private final ProjectAccessGuard accessGuard;

    public MonitoringController(
            MonitoringService monitoringService,
            DeploymentService deploymentService,
            ProjectAccessGuard accessGuard) {
        this.monitoringService = monitoringService;
        this.deploymentService = deploymentService;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/api/v1/projects/{projectId}/health")
    @Operation(
            summary = "Health of the project's live deployment",
            description =
                    "Reports container state, uptime, restarts and the latest resource sample. Returns "
                            + "NO_DEPLOYMENT when nothing has been deployed yet.")
    public ProjectHealthResponse projectHealth(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        accessGuard.require(projectId, user.userId(), Permission.VIEW_MONITORING);
        return monitoringService.projectHealth(projectId);
    }

    @GetMapping("/api/v1/deployments/{deploymentId}/metrics")
    @Operation(
            summary = "Resource usage for a deployment",
            description = "Current sample from the engine plus stored history for charting.")
    public DeploymentMetricsResponse deploymentMetrics(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID deploymentId,
            @RequestParam(name = "windowMinutes", defaultValue = "60") @Min(5) @Max(4320)
                    int windowMinutes) {
        Deployment deployment = deploymentService.require(deploymentId);
        accessGuard.require(deployment.getProjectId(), user.userId(), Permission.VIEW_MONITORING);
        return monitoringService.deploymentMetrics(deploymentId, Duration.ofMinutes(windowMinutes));
    }
}
