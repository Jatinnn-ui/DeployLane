package com.deployforge.deployment;

import com.deployforge.project.ProjectDeploymentSnapshotProvider;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supplies "latest deployment" data to the project module.
 *
 * <p>The adapter side of {@link ProjectDeploymentSnapshotProvider}: deployment depends on project, so
 * this inversion keeps the modules layered while still letting a projects list show live status.
 *
 * <p>One query for the whole page, not one per project - the dashboard renders this for every card.
 */
@Component
public class LatestDeploymentSnapshotProvider implements ProjectDeploymentSnapshotProvider {

    private final DeploymentRepository deploymentRepository;

    public LatestDeploymentSnapshotProvider(DeploymentRepository deploymentRepository) {
        this.deploymentRepository = deploymentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, DeploymentSnapshot> latestByProject(Collection<UUID> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Map.of();
        }
        List<Deployment> latest = deploymentRepository.findLatestForProjects(List.copyOf(projectIds));
        Map<UUID, DeploymentSnapshot> snapshots = new HashMap<>(latest.size());
        for (Deployment deployment : latest) {
            snapshots.put(
                    deployment.getProjectId(),
                    new DeploymentSnapshot(
                            deployment.getId(),
                            deployment.getDeploymentNumber(),
                            deployment.getStatus().name(),
                            deployment.getBranch(),
                            deployment.shortCommitSha(),
                            deployment.getCommitMessage(),
                            deployment.getDeploymentUrl(),
                            deployment.getCreatedAt(),
                            deployment.getFinishedAt(),
                            deployment.getDurationMs()));
        }
        return snapshots;
    }
}
