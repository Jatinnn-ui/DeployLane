package com.deployforge.log;

import com.deployforge.common.api.CursorPageResponse;
import com.deployforge.deployment.Deployment;
import com.deployforge.deployment.DeploymentService;
import com.deployforge.log.DeploymentLogService.LogEntryResponse;
import com.deployforge.project.ProjectAccessGuard;
import com.deployforge.security.AuthenticatedUser;
import com.deployforge.workspace.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deployment log access.
 *
 * <p>Cursor paginated rather than offset paginated: logs are appended while a client is reading them, so
 * an offset would skip or duplicate lines. {@code afterSequence} is stable no matter what arrives next.
 */
@RestController
@RequestMapping("/api/v1/deployments/{deploymentId}/logs")
@Validated
@Tag(name = "Logs", description = "Deployment build and runtime logs")
public class DeploymentLogController {

    private static final int MAX_DOWNLOAD_LINES = 5000;

    private final DeploymentLogService logService;
    private final DeploymentService deploymentService;
    private final ProjectAccessGuard accessGuard;

    public DeploymentLogController(
            DeploymentLogService logService,
            DeploymentService deploymentService,
            ProjectAccessGuard accessGuard) {
        this.logService = logService;
        this.deploymentService = deploymentService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    @Operation(
            summary = "Read deployment logs",
            description =
                    "Pass afterSequence with the last sequence you have seen to fetch only new lines. "
                            + "Values are already redacted server side.")
    public CursorPageResponse<LogEntryResponse> read(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID deploymentId,
            @RequestParam(name = "afterSequence", required = false) Long afterSequence,
            @RequestParam(name = "limit", defaultValue = "200") @Min(1) @Max(500) int limit,
            @RequestParam(name = "level", required = false) LogLevel level,
            @RequestParam(name = "source", required = false) LogSource source) {
        authorize(deploymentId, user);
        return logService.read(deploymentId, afterSequence, limit, level, source);
    }

    @GetMapping("/download")
    @Operation(summary = "Download the log as a plain text file")
    public ResponseEntity<String> download(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID deploymentId) {
        Deployment deployment = authorize(deploymentId, user);
        List<LogEntryResponse> entries = logService.tail(deploymentId, MAX_DOWNLOAD_LINES);

        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
        StringBuilder body = new StringBuilder();
        body.append("# DeployForge deployment #")
                .append(deployment.getDeploymentNumber())
                .append(" - ")
                .append(deployment.getStatus())
                .append('\n');
        for (LogEntryResponse entry : entries) {
            body.append(formatter.format(entry.timestamp()))
                    .append("  ")
                    .append(String.format("%-6s", entry.level()))
                    .append(String.format("%-12s", entry.source()))
                    .append(entry.message())
                    .append('\n');
        }

        String filename = "deployment-" + deployment.getDeploymentNumber() + ".log";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(body.toString());
    }

    private Deployment authorize(UUID deploymentId, AuthenticatedUser user) {
        Deployment deployment = deploymentService.require(deploymentId);
        accessGuard.require(deployment.getProjectId(), user.userId(), Permission.VIEW_LOGS);
        return deployment;
    }
}
