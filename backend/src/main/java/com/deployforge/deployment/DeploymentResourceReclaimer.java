package com.deployforge.deployment;

import com.deployforge.common.util.SafePaths;
import com.deployforge.config.DeployForgeProperties;
import com.deployforge.project.ProjectResourceReclaimer;
import com.deployforge.runtime.DeploymentRuntime;
import com.deployforge.runtime.PortAllocator;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases the infrastructure a project owns, before its rows are deleted.
 *
 * <p>Two sources of truth are used together: the deployment rows (what DeployForge believes it started)
 * and the Docker labels (what is actually running). A container whose row was already lost still gets
 * cleaned up because it carries {@code deployforge.projectId}.
 *
 * <p>Nothing outside those labels is ever touched.
 */
@Component
public class DeploymentResourceReclaimer implements ProjectResourceReclaimer {

    private static final Logger log = LoggerFactory.getLogger(DeploymentResourceReclaimer.class);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);

    private final DeploymentRepository deploymentRepository;
    private final DeploymentRuntime runtime;
    private final PortAllocator portAllocator;
    private final DeployForgeProperties properties;

    public DeploymentResourceReclaimer(
            DeploymentRepository deploymentRepository,
            DeploymentRuntime runtime,
            PortAllocator portAllocator,
            DeployForgeProperties properties) {
        this.deploymentRepository = deploymentRepository;
        this.runtime = runtime;
        this.portAllocator = portAllocator;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public void reclaim(UUID projectId) {
        List<Deployment> deployments = deploymentRepository.findByProjectId(projectId);

        for (Deployment deployment : deployments) {
            if (deployment.getContainerId() != null) {
                runtime.stop(deployment.getContainerId(), STOP_TIMEOUT);
                runtime.remove(deployment.getContainerId());
            }
            if (deployment.getHostPort() != null) {
                portAllocator.releaseLease(deployment.getHostPort());
            }
            if (deployment.getImageTag() != null) {
                runtime.removeImage(deployment.getImageTag());
            }
            deleteWorkDirectory(deployment.getId());
        }

        // Catch containers whose deployment row is gone, using the labels as the fallback index.
        String projectLabel = projectId.toString();
        runtime.listManagedContainers().stream()
                .filter(container -> projectLabel.equals(container.projectId()))
                .forEach(
                        container -> {
                            runtime.stop(container.containerId(), STOP_TIMEOUT);
                            runtime.remove(container.containerId());
                        });

        String imagePrefix = "deployforge/" + projectId + ":";
        runtime.listManagedImages().stream()
                .filter(tag -> tag.startsWith(imagePrefix))
                .forEach(runtime::removeImage);

        log.info("project_resources_reclaimed project={} deployments={}", projectId, deployments.size());
    }

    private void deleteWorkDirectory(UUID deploymentId) {
        Path root = Path.of(properties.deployment().rootPath()).toAbsolutePath().normalize();
        SafePaths.deleteQuietly(SafePaths.resolveInside(root, deploymentId.toString()));
    }
}
