package com.deployforge.project;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Port that lets the project module show "last deployment" information without depending on the
 * deployment module.
 *
 * <p>Deployments already depend on projects; a reverse dependency would create a cycle. The
 * deployment module implements this interface instead (dependency inversion), so the projects list
 * can stay a single, N+1-free query while the modules remain layered.
 */
public interface ProjectDeploymentSnapshotProvider {

    Map<UUID, DeploymentSnapshot> latestByProject(Collection<UUID> projectIds);

    record DeploymentSnapshot(
            UUID deploymentId,
            int deploymentNumber,
            String status,
            String branch,
            String commitSha,
            String commitMessage,
            String deploymentUrl,
            Instant createdAt,
            Instant finishedAt,
            Long durationMs) {}
}
