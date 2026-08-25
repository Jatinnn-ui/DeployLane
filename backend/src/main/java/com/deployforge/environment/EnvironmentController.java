package com.deployforge.environment;

import com.deployforge.environment.dto.EnvironmentVariableDtos.BulkVariablesRequest;
import com.deployforge.environment.dto.EnvironmentVariableDtos.BulkVariablesResponse;
import com.deployforge.environment.dto.EnvironmentVariableDtos.CreateVariableRequest;
import com.deployforge.environment.dto.EnvironmentVariableDtos.EnvironmentVariableResponse;
import com.deployforge.environment.dto.EnvironmentVariableDtos.UpdateVariableRequest;
import com.deployforge.environment.dto.EnvironmentVariableDtos.VariableListResponse;
import com.deployforge.project.dto.ProjectDtos.CreateEnvironmentRequest;
import com.deployforge.project.dto.ProjectDtos.EnvironmentResponse;
import com.deployforge.project.dto.ProjectDtos.UpdateEnvironmentRequest;
import com.deployforge.security.AuthenticatedUser;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Environments and their encrypted variables.
 *
 * <p>Reads return variable <em>metadata</em> only. There is no endpoint anywhere in this API that
 * returns a stored value - not for owners, not for admins.
 */
@RestController
@Tag(name = "Environments", description = "Deployment targets and encrypted environment variables")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @GetMapping("/api/v1/projects/{projectId}/environments")
    public List<EnvironmentResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return environmentService.list(projectId, user.userId());
    }

    @PostMapping("/api/v1/projects/{projectId}/environments")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public EnvironmentResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateEnvironmentRequest request) {
        return environmentService.create(projectId, user.userId(), request);
    }

    @PatchMapping("/api/v1/environments/{environmentId}")
    @Operation(summary = "Change the tracked branch or toggle auto deploy")
    public EnvironmentResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID environmentId,
            @Valid @RequestBody UpdateEnvironmentRequest request) {
        return environmentService.update(environmentId, user.userId(), request);
    }

    @DeleteMapping("/api/v1/environments/{environmentId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID environmentId) {
        environmentService.delete(environmentId, user.userId());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ variables

    @GetMapping("/api/v1/environments/{environmentId}/variables")
    @Operation(
            summary = "List environment variables",
            description = "Returns names and timestamps. Values are never returned by this API.")
    public VariableListResponse listVariables(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID environmentId) {
        List<EnvironmentVariableResponse> variables =
                environmentService.listVariables(environmentId, user.userId());
        return new VariableListResponse(environmentId, variables, variables.size());
    }

    @PostMapping("/api/v1/environments/{environmentId}/variables")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a variable", description = "The value is encrypted before storage")
    public EnvironmentVariableResponse createVariable(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID environmentId,
            @Valid @RequestBody CreateVariableRequest request) {
        return environmentService.createVariable(
                environmentId, user.userId(), request.key(), request.value());
    }

    @PostMapping("/api/v1/environments/{environmentId}/variables/bulk")
    @Operation(
            summary = "Bulk paste a .env block",
            description =
                    "Accepts comments, blank lines, 'export ' prefixes and quoted values. Malformed lines are "
                            + "reported instead of aborting the whole paste.")
    public BulkVariablesResponse bulkUpdate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID environmentId,
            @Valid @RequestBody BulkVariablesRequest request) {
        return environmentService.bulkUpdateVariables(
                environmentId, user.userId(), request.content(), request.replaceExisting());
    }

    @PatchMapping("/api/v1/environment-variables/{variableId}")
    @Operation(summary = "Rotate a variable's value")
    public EnvironmentVariableResponse updateVariable(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID variableId,
            @Valid @RequestBody UpdateVariableRequest request) {
        return environmentService.updateVariable(variableId, user.userId(), request.value());
    }

    @DeleteMapping("/api/v1/environment-variables/{variableId}")
    public ResponseEntity<Void> deleteVariable(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID variableId) {
        environmentService.deleteVariable(variableId, user.userId());
        return ResponseEntity.noContent().build();
    }
}
