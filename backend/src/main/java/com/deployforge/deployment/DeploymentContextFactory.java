package com.deployforge.deployment;

import com.deployforge.common.error.Exceptions.NotFoundException;
import com.deployforge.deployment.pipeline.DeploymentContext;
import com.deployforge.deployment.queue.DeploymentQueue;
import com.deployforge.environment.Environment;
import com.deployforge.environment.EnvironmentRepository;
import com.deployforge.project.Project;
import com.deployforge.project.ProjectRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles a {@link DeploymentContext} from persisted state.
 *
 * <p>Everything the pipeline needs is copied out here, inside one short read transaction, precisely so
 * that no entity or persistence context is carried into the long running part of the deployment.
 */
@Service
public class DeploymentContextFactory {

    private final DeploymentRepository deploymentRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final DeploymentCredentialsService credentialsService;
    private final DeploymentQueue queue;

    public DeploymentContextFactory(
            DeploymentRepository deploymentRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            DeploymentCredentialsService credentialsService,
            DeploymentQueue queue) {
        this.deploymentRepository = deploymentRepository;
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.credentialsService = credentialsService;
        this.queue = queue;
    }

    @Transactional(readOnly = true)
    public DeploymentContext create(UUID deploymentId) {
        Deployment deployment =
                deploymentRepository
                        .findById(deploymentId)
                        .orElseThrow(() -> NotFoundException.of("Deployment", deploymentId));
        Project project =
                projectRepository
                        .findById(deployment.getProjectId())
                        .orElseThrow(() -> NotFoundException.of("Project", deployment.getProjectId()));
        Environment environment =
                environmentRepository
                        .findById(deployment.getEnvironmentId())
                        .orElseThrow(
                                () -> NotFoundException.of("Environment", deployment.getEnvironmentId()));

        DeploymentContext context =
                new DeploymentContext(
                        deployment.getId(),
                        project.getId(),
                        environment.getId(),
                        project.getWorkspaceId(),
                        deployment.getDeploymentNumber(),
                        deployment.getTriggerType(),
                        project.getSlug(),
                        project.getName(),
                        project.getRepositoryOwner(),
                        project.getRepositoryName(),
                        cloneUrl(project),
                        project.isRepositoryPrivate(),
                        deployment.getBranch(),
                        () -> queue.isCancellationRequested(deploymentId));

        // Configuration snapshot taken when the deployment was created, not the project's current state.
        context.setFramework(deployment.getFramework());
        context.setRuntime(deployment.getRuntime());
        context.setRootDirectory(deployment.getRootDirectory());
        context.setInstallCommand(deployment.getInstallCommand());
        context.setBuildCommand(deployment.getBuildCommand());
        context.setStartCommand(deployment.getStartCommand());
        context.setDockerfilePath(deployment.getDockerfilePath());
        context.setContainerPort(
                deployment.getContainerPort() == null ? 0 : deployment.getContainerPort());
        context.setHealthCheckPath(project.getHealthCheckPath());
        context.setCommitSha(deployment.getCommitSha());
        context.setCommitMessage(deployment.getCommitMessage());

        if (deployment.getTriggerType() == DeploymentTriggerType.ROLLBACK
                && deployment.getSourceDeploymentId() != null) {
            deploymentRepository
                    .findById(deployment.getSourceDeploymentId())
                    .filter(Deployment::hasReusableImage)
                    .ifPresent(source -> context.setReusableImageTag(source.getImageTag()));
        }

        if (!context.isImageReuse()) {
            credentialsService
                    .resolveCloneToken(project, deployment.getCreatedBy())
                    .ifPresent(context::setGithubToken);
        }

        return context;
    }

    /** GitHub HTTPS clone URL derived from the stored repository identity. */
    private String cloneUrl(Project project) {
        return "https://github.com/"
                + project.getRepositoryOwner()
                + "/"
                + project.getRepositoryName()
                + ".git";
    }
}
