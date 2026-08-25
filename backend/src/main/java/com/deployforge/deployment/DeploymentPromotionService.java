package com.deployforge.deployment;

import com.deployforge.log.DeploymentLogService;
import com.deployforge.log.LogLevel;
import com.deployforge.log.LogSource;
import com.deployforge.runtime.DeploymentRuntime;
import com.deployforge.runtime.DomainRoutingService;
import com.deployforge.runtime.PortAllocator;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Switches an environment's traffic to a new deployment, then retires the old one.
 *
 * <p>This ordering is the whole point of the design: the previous container keeps serving until the new
 * one has passed its health check. A failed deployment therefore changes nothing - the old version is
 * still running and still routed - which is the difference between a deploy that fails and an outage.
 *
 * <pre>
 *   old READY  ────────────────────────────────►  stopped
 *   new        starting ─► healthy ─► routed ─┘
 * </pre>
 */
@Service
public class DeploymentPromotionService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentPromotionService.class);
    private static final Duration GRACEFUL_STOP = Duration.ofSeconds(10);

    private final DeploymentRepository deploymentRepository;
    private final DeploymentStateService stateService;
    private final DomainRoutingService routingService;
    private final DeploymentRuntime runtime;
    private final PortAllocator portAllocator;
    private final DeploymentLogService logService;

    public DeploymentPromotionService(
            DeploymentRepository deploymentRepository,
            DeploymentStateService stateService,
            DomainRoutingService routingService,
            DeploymentRuntime runtime,
            PortAllocator portAllocator,
            DeploymentLogService logService) {
        this.deploymentRepository = deploymentRepository;
        this.stateService = stateService;
        this.routingService = routingService;
        this.runtime = runtime;
        this.portAllocator = portAllocator;
        this.logService = logService;
    }

    /**
     * Routes traffic to {@code deploymentId} and stops whatever was live before it.
     *
     * @return the public URL of the newly promoted deployment
     */
    public String promote(
            UUID projectId,
            UUID environmentId,
            UUID deploymentId,
            String projectSlug,
            int hostPort) {

        Optional<Deployment> previous = findPromoted(environmentId, deploymentId);

        DomainRoutingService.RoutingTarget target =
                routingService.publish(projectId, environmentId, deploymentId, projectSlug, hostPort);
        stateService.markReady(deploymentId, target.url());

        previous.ifPresent(
                old -> {
                    logService.append(
                            deploymentId,
                            LogLevel.INFO,
                            LogSource.SYSTEM,
                            "Traffic switched from deployment #" + old.getDeploymentNumber() + ". Stopping it now.");
                    retire(old);
                });

        return target.url();
    }

    @Transactional(readOnly = true)
    public Optional<Deployment> findPromoted(UUID environmentId, UUID excludeDeploymentId) {
        return deploymentRepository
                .findFirstByEnvironmentIdAndPromotedTrueOrderByDeploymentNumberDesc(environmentId)
                .filter(deployment -> !deployment.getId().equals(excludeDeploymentId));
    }

    /** Stops and removes a superseded deployment's container, keeping its image for rollback. */
    public void retire(Deployment deployment) {
        if (deployment.getContainerId() != null) {
            runtime.stop(deployment.getContainerId(), GRACEFUL_STOP);
            runtime.remove(deployment.getContainerId());
        }
        if (deployment.getHostPort() != null) {
            portAllocator.releaseLease(deployment.getHostPort());
        }
        stateService.markStopped(deployment.getId());
        log.info(
                "deployment_retired deployment={} number={}",
                deployment.getId(),
                deployment.getDeploymentNumber());
    }

    /** Stops every live container of a project. Used when archiving or deleting. */
    @Transactional(readOnly = true)
    public List<Deployment> liveDeployments(UUID projectId) {
        return deploymentRepository.findByProjectIdAndStatusIn(
                projectId,
                List.of(
                        DeploymentStatus.READY,
                        DeploymentStatus.HEALTH_CHECKING,
                        DeploymentStatus.STARTING));
    }
}
