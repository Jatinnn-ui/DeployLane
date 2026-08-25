package com.deployforge.project.dto;

import com.deployforge.detection.Framework;
import com.deployforge.detection.RuntimeType;
import com.deployforge.environment.EnvironmentType;
import com.deployforge.project.ProjectDeploymentSnapshotProvider.DeploymentSnapshot;
import com.deployforge.project.ProjectStatus;
import com.deployforge.workspace.Permission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Project API payloads. */
public final class ProjectDtos {

    private ProjectDtos() {}

    /** Build configuration. Any {@code null} means "use the detected framework default". */
    public record BuildConfig(
            String rootDirectory,
            String installCommand,
            String buildCommand,
            String startCommand,
            String dockerfilePath,
            Integer port,
            String healthCheckPath) {}

    public record RepositoryRef(
            String owner, String name, String fullName, String url, boolean isPrivate, Long githubId) {}

    public record EnvironmentResponse(
            UUID id,
            UUID projectId,
            String name,
            EnvironmentType type,
            String branch,
            boolean autoDeployEnabled,
            long variableCount,
            Instant createdAt) {}

    public record ProjectResponse(
            UUID id,
            UUID workspaceId,
            String name,
            String slug,
            String description,
            RepositoryRef repository,
            String defaultBranch,
            Framework framework,
            String frameworkLabel,
            RuntimeType runtime,
            ProjectStatus status,
            BuildConfig buildConfig,
            List<EnvironmentResponse> environments,
            DeploymentSnapshot latestDeployment,
            Set<Permission> permissions,
            Instant createdAt,
            Instant updatedAt) {}

    public record ProjectSummaryResponse(
            UUID id,
            UUID workspaceId,
            String name,
            String slug,
            RepositoryRef repository,
            Framework framework,
            String frameworkLabel,
            ProjectStatus status,
            String productionBranch,
            DeploymentSnapshot latestDeployment,
            Instant createdAt) {}

    /** A single variable supplied at import time so the first deploy can succeed immediately. */
    public record InitialVariable(
            @NotBlank @Size(max = 128) String key, @NotBlank @Size(max = 8192) String value) {}

    public record ImportProjectRequest(
            UUID workspaceId,
            @NotBlank @Size(max = 100) String repositoryOwner,
            @NotBlank @Size(max = 100) String repositoryName,
            @NotBlank @Size(max = 255) String branch,
            @Size(max = 120) String name,
            @Size(max = 120)
                    @Pattern(
                            regexp = "^$|^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$",
                            message = "must be lowercase alphanumeric with single hyphens")
                    String slug,
            @Size(max = 1000) String description,
            Framework framework,
            @Valid BuildConfig buildConfig,
            boolean autoDeployEnabled,
            @Valid List<InitialVariable> environmentVariables) {}

    public record UpdateProjectRequest(
            @Size(min = 1, max = 120) String name,
            @Size(max = 1000) String description,
            Framework framework,
            ProjectStatus status,
            @Valid BuildConfig buildConfig) {}

    public record CreateEnvironmentRequest(
            @NotBlank @Size(max = 64) String name,
            EnvironmentType type,
            @NotBlank @Size(max = 255) String branch,
            boolean autoDeployEnabled) {}

    public record UpdateEnvironmentRequest(
            @Size(max = 255) String branch, Boolean autoDeployEnabled) {}

    public record ProjectListRequest(
            UUID workspaceId, @Min(0) int page, @Min(1) @Max(100) int size) {}
}
