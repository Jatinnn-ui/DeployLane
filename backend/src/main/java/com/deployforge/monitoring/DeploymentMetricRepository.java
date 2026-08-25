package com.deployforge.monitoring;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeploymentMetricRepository extends JpaRepository<DeploymentMetric, Long> {

    List<DeploymentMetric> findByDeploymentIdOrderBySampledAtDesc(UUID deploymentId, Pageable pageable);

    List<DeploymentMetric> findByDeploymentIdAndSampledAtAfterOrderBySampledAtAsc(
            UUID deploymentId, Instant after);

    @Modifying
    @Query("delete from DeploymentMetric m where m.sampledAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
