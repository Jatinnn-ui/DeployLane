package com.deployforge.log;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeploymentLogRepository extends JpaRepository<DeploymentLog, Long> {

    /** Cursor page: everything after a sequence number, oldest first. */
    List<DeploymentLog> findByDeploymentIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
            UUID deploymentId, long afterSequence, Pageable pageable);

    List<DeploymentLog> findByDeploymentIdOrderBySequenceNumberAsc(UUID deploymentId, Pageable pageable);

    /** Tail of a deployment's logs, used when feeding evidence to the AI analyzer. */
    List<DeploymentLog> findByDeploymentIdOrderBySequenceNumberDesc(UUID deploymentId, Pageable pageable);

    List<DeploymentLog> findByDeploymentIdAndLevelOrderBySequenceNumberDesc(
            UUID deploymentId, LogLevel level, Pageable pageable);

    @Query("select coalesce(max(l.sequenceNumber), 0) from DeploymentLog l where l.deploymentId = :deploymentId")
    long findMaxSequence(@Param("deploymentId") UUID deploymentId);

    long countByDeploymentId(UUID deploymentId);

    @Modifying
    @Query("delete from DeploymentLog l where l.loggedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
