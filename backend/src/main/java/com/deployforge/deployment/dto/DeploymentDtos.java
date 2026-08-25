package com.deployforge.deployment.dto;

import com.deployforge.deployment.DeploymentStatus;
import com.deployforge.deployment.DeploymentTriggerType;
import com.deployforge.detection.Framework;
import com.deployforge.detection.RuntimeType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Deployment API payloads. */
public final class DeploymentDtos {

    private DeploymentDtos() {}

    public record CommitInfo(
            String sha, String shortSha, String message, String author, String url) {}

    public record FailureInfo(String stage, String message, Integer exitCode) {}

    public record TimingInfo(
            Instant queuedAt, Instant startedAt, Instant finishedAt, Long durationMs) {}

    public record ActorInfo(UUID id, String name, String avatarUrl) {}

    public record BuildSnapshot(
            Framework framework,
            String frameworkLabel,
            RuntimeType runtime,
            String rootDirectory,
            String installCommand,
            String buildCommand,
            String startCommand,
            String dockerfilePath,
            Integer containerPort) {}

    public record ContainerInfo(
            String containerId, Integer hostPort, Integer containerPort, String imageTag) {}

    public record StepResponse(
            String step,
            String label,
            String status,
            int sequence,
            Instant startedAt,
            Instant finishedAt,
            Long durationMs,
            String detail) {}

    public record DeploymentResponse(
            UUID id,
            UUID projectId,
            String projectName,
            String projectSlug,
            UUID environmentId,
            String environmentName,
            int deploymentNumber,
            DeploymentStatus status,
            String statusLabel,
            DeploymentTriggerType triggerType,
            String branch,
            CommitInfo commit,
            BuildSnapshot build,
            ContainerInfo container,
            String url,
            boolean promoted,
            boolean cancellationRequested,
            FailureInfo failure,
            TimingInfo timing,
            ActorInfo createdBy,
            List<StepResponse> steps,
            Instant createdAt) {}

    public record DeploymentSummaryResponse(
            UUID id,
            UUID projectId,
            int deploymentNumber,
            DeploymentStatus status,
            String statusLabel,
            DeploymentTriggerType triggerType,
            String environmentName,
            String branch,
            String commitSha,
            String shortCommitSha,
            String commitMessage,
            String url,
            boolean promoted,
            String failureMessage,
            Long durationMs,
            Instant createdAt,
            Instant finishedAt) {}

    /**
     * Manual deployment request.
     *
     * @param environmentId defaults to the project's production environment
     * @param branch defaults to the environment's tracked branch
     */
    public record CreateDeploymentRequest(
            UUID environmentId, @Size(max = 255) String branch, @Size(max = 40) String commitSha) {}

    public record RollbackRequest(@NotNull UUID targetDeploymentId) {}

    public record RollbackCandidate(
            UUID deploymentId,
            int deploymentNumber,
            String commitSha,
            String shortCommitSha,
            String commitMessage,
            Instant createdAt,
            boolean imageAvailable) {}

    public record QueueStatusResponse(long queueDepth, int workerConcurrency) {}
}
