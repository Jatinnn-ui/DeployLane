package com.deployforge.deployment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentStepRepository extends JpaRepository<DeploymentStep, UUID> {

    List<DeploymentStep> findByDeploymentIdOrderBySequenceNumberAsc(UUID deploymentId);

    Optional<DeploymentStep> findByDeploymentIdAndStep(UUID deploymentId, DeploymentStepName step);

    List<DeploymentStep> findByDeploymentIdIn(List<UUID> deploymentIds);
}
