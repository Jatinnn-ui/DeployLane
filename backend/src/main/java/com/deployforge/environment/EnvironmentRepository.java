package com.deployforge.environment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {

    List<Environment> findByProjectIdOrderByTypeAscNameAsc(UUID projectId);

    Optional<Environment> findByProjectIdAndName(UUID projectId, String name);

    Optional<Environment> findFirstByProjectIdAndType(UUID projectId, EnvironmentType type);

    List<Environment> findByProjectIdIn(List<UUID> projectIds);

    /** Auto deploy candidates for an incoming push. */
    List<Environment> findByProjectIdAndBranchAndAutoDeployEnabledTrue(UUID projectId, String branch);

    void deleteByProjectId(UUID projectId);
}
