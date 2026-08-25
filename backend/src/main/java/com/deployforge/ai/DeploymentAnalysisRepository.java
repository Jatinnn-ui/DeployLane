package com.deployforge.ai;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentAnalysisRepository extends JpaRepository<DeploymentAnalysis, UUID> {

    Optional<DeploymentAnalysis> findByDeploymentId(UUID deploymentId);

    List<DeploymentAnalysis> findByDeploymentIdIn(List<UUID> deploymentIds);
}
