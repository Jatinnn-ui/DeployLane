package com.deployforge.deployment;

import com.deployforge.deployment.dto.DeploymentDtos.ActorInfo;
import com.deployforge.deployment.dto.DeploymentDtos.BuildSnapshot;
import com.deployforge.deployment.dto.DeploymentDtos.CommitInfo;
import com.deployforge.deployment.dto.DeploymentDtos.ContainerInfo;
import com.deployforge.deployment.dto.DeploymentDtos.DeploymentResponse;
import com.deployforge.deployment.dto.DeploymentDtos.DeploymentSummaryResponse;
import com.deployforge.deployment.dto.DeploymentDtos.FailureInfo;
import com.deployforge.deployment.dto.DeploymentDtos.StepResponse;
import com.deployforge.deployment.dto.DeploymentDtos.TimingInfo;
import com.deployforge.project.Project;
import com.deployforge.user.User;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Entity to DTO mapping for deployments.
 *
 * <p>Its own class so no controller or service is tempted to return a JPA entity, and so the container
 * id is consistently truncated - the full 64 character id is noise in a UI and mildly sensitive.
 */
@Component
public class DeploymentMapper {

    public DeploymentResponse toResponse(
            Deployment deployment,
            Project project,
            String environmentName,
            User createdBy,
            List<DeploymentStep> steps,
            String commitUrl) {

        return new DeploymentResponse(
                deployment.getId(),
                deployment.getProjectId(),
                project == null ? null : project.getName(),
                project == null ? null : project.getSlug(),
                deployment.getEnvironmentId(),
                environmentName,
                deployment.getDeploymentNumber(),
                deployment.getStatus(),
                deployment.getStatus().label(),
                deployment.getTriggerType(),
                deployment.getBranch(),
                new CommitInfo(
                        deployment.getCommitSha(),
                        deployment.shortCommitSha(),
                        deployment.getCommitMessage(),
                        deployment.getCommitAuthor(),
                        commitUrl),
                new BuildSnapshot(
                        deployment.getFramework(),
                        deployment.getFramework() == null ? null : deployment.getFramework().displayName(),
                        deployment.getRuntime(),
                        deployment.getRootDirectory(),
                        deployment.getInstallCommand(),
                        deployment.getBuildCommand(),
                        deployment.getStartCommand(),
                        deployment.getDockerfilePath(),
                        deployment.getContainerPort()),
                new ContainerInfo(
                        shortContainerId(deployment.getContainerId()),
                        deployment.getHostPort(),
                        deployment.getContainerPort(),
                        deployment.getImageTag()),
                deployment.getDeploymentUrl(),
                deployment.isPromoted(),
                deployment.isCancellationRequested(),
                deployment.getFailureStage() == null && deployment.getFailureMessage() == null
                        ? null
                        : new FailureInfo(
                                deployment.getFailureStage() == null
                                        ? null
                                        : deployment.getFailureStage().name(),
                                deployment.getFailureMessage(),
                                deployment.getExitCode()),
                new TimingInfo(
                        deployment.getQueuedAt(),
                        deployment.getStartedAt(),
                        deployment.getFinishedAt(),
                        deployment.getDurationMs()),
                createdBy == null
                        ? null
                        : new ActorInfo(createdBy.getId(), createdBy.displayName(), createdBy.getAvatarUrl()),
                steps == null ? List.of() : steps.stream().map(this::toStepResponse).toList(),
                deployment.getCreatedAt());
    }

    public DeploymentSummaryResponse toSummary(Deployment deployment, String environmentName) {
        return new DeploymentSummaryResponse(
                deployment.getId(),
                deployment.getProjectId(),
                deployment.getDeploymentNumber(),
                deployment.getStatus(),
                deployment.getStatus().label(),
                deployment.getTriggerType(),
                environmentName,
                deployment.getBranch(),
                deployment.getCommitSha(),
                deployment.shortCommitSha(),
                deployment.getCommitMessage(),
                deployment.getDeploymentUrl(),
                deployment.isPromoted(),
                deployment.getFailureMessage(),
                deployment.getDurationMs(),
                deployment.getCreatedAt(),
                deployment.getFinishedAt());
    }

    public StepResponse toStepResponse(DeploymentStep step) {
        return new StepResponse(
                step.getStep().name(),
                step.getStep().label(),
                step.getStatus().name(),
                step.getSequenceNumber(),
                step.getStartedAt(),
                step.getFinishedAt(),
                step.getDurationMs(),
                step.getDetail());
    }

    private String shortContainerId(String containerId) {
        if (containerId == null) {
            return null;
        }
        return containerId.length() > 12 ? containerId.substring(0, 12) : containerId;
    }
}
