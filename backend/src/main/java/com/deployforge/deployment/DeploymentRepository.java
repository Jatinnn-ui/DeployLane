package com.deployforge.deployment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

    Page<Deployment> findByProjectIdOrderByDeploymentNumberDesc(UUID projectId, Pageable pageable);

    Page<Deployment> findByProjectIdAndEnvironmentIdOrderByDeploymentNumberDesc(
            UUID projectId, UUID environmentId, Pageable pageable);

    Optional<Deployment> findFirstByProjectIdOrderByDeploymentNumberDesc(UUID projectId);

    Optional<Deployment> findFirstByEnvironmentIdAndPromotedTrueOrderByDeploymentNumberDesc(
            UUID environmentId);

    Optional<Deployment> findFirstByEnvironmentIdAndStatusOrderByDeploymentNumberDesc(
            UUID environmentId, DeploymentStatus status);

    List<Deployment> findByEnvironmentIdAndStatusIn(UUID environmentId, List<DeploymentStatus> statuses);

    List<Deployment> findByProjectIdAndStatusIn(UUID projectId, List<DeploymentStatus> statuses);

    List<Deployment> findByProjectId(UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, DeploymentStatus status);

    /** Highest deployment number in a project; used to allocate the next one. */
    @Query("select coalesce(max(d.deploymentNumber), 0) from Deployment d where d.projectId = :projectId")
    int findMaxDeploymentNumber(@Param("projectId") UUID projectId);

    /**
     * Latest deployment per project in a single query.
     *
     * <p>Written as a correlated max() subquery rather than N lookups, because the projects list
     * renders this for every card.
     */
    @Query(
            """
            select d from Deployment d
            where d.deploymentNumber = (
                select max(d2.deploymentNumber) from Deployment d2 where d2.projectId = d.projectId
            )
            and d.projectId in :projectIds
            """)
    List<Deployment> findLatestForProjects(@Param("projectIds") List<UUID> projectIds);

    /** Successful deployments that can be rolled back to. */
    @Query(
            """
            select d from Deployment d
            where d.environmentId = :environmentId
              and d.status in (com.deployforge.deployment.DeploymentStatus.READY,
                               com.deployforge.deployment.DeploymentStatus.STOPPED)
              and d.imageTag is not null
              and d.id <> :excludeId
            order by d.deploymentNumber desc
            """)
    List<Deployment> findRollbackCandidates(
            @Param("environmentId") UUID environmentId, @Param("excludeId") UUID excludeId, Pageable pageable);

    /** Deployments left mid-flight by a crash, so a restarting worker can fail them cleanly. */
    @Query(
            """
            select d from Deployment d
            where d.status not in (com.deployforge.deployment.DeploymentStatus.READY,
                                   com.deployforge.deployment.DeploymentStatus.FAILED,
                                   com.deployforge.deployment.DeploymentStatus.CANCELLED,
                                   com.deployforge.deployment.DeploymentStatus.STOPPED)
            """)
    List<Deployment> findAllActive();

    List<Deployment> findByStatusAndFinishedAtBefore(DeploymentStatus status, Instant cutoff);

    /** Promoted deployments with a container, i.e. everything the metrics sampler should poll. */
    @Query(
            """
            select d from Deployment d
            where d.status = com.deployforge.deployment.DeploymentStatus.READY
              and d.promoted = true
              and d.containerId is not null
            """)
    List<Deployment> findLiveContainers();

    /** Host ports currently claimed by deployments that are live or coming up. */
    @Query(
            """
            select d.hostPort from Deployment d
            where d.hostPort is not null
              and d.status in (com.deployforge.deployment.DeploymentStatus.STARTING,
                               com.deployforge.deployment.DeploymentStatus.HEALTH_CHECKING,
                               com.deployforge.deployment.DeploymentStatus.READY)
            """)
    List<Integer> findClaimedHostPorts();

    @Query(
            """
            select d from Deployment d
            where d.projectId in :projectIds
              and d.status = com.deployforge.deployment.DeploymentStatus.FAILED
            order by d.createdAt desc
            """)
    List<Deployment> findRecentFailures(@Param("projectIds") List<UUID> projectIds, Pageable pageable);
}
