package com.deployforge.deployment.queue;

import com.deployforge.config.DeployForgeProperties;
import com.deployforge.deployment.Deployment;
import com.deployforge.deployment.DeploymentContextFactory;
import com.deployforge.deployment.DeploymentRepository;
import com.deployforge.deployment.DeploymentStateService;
import com.deployforge.deployment.DeploymentStatus;
import com.deployforge.deployment.pipeline.DeploymentContext;
import com.deployforge.deployment.pipeline.DeploymentPipeline;
import com.deployforge.log.DeploymentLogService;
import com.deployforge.log.LogLevel;
import com.deployforge.log.LogSource;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes the deployment queue.
 *
 * <p>Why this exists at all: a deployment takes minutes, so it cannot run on the HTTP thread that
 * requested it. The API creates a {@code QUEUED} row, returns immediately, and these workers pick it up.
 *
 * <p>Behaviour that matters in production:
 *
 * <ul>
 *   <li><b>Bounded concurrency.</b> A single node cannot build many images at once; extra work waits in
 *       the queue instead of thrashing the host.
 *   <li><b>Per environment exclusivity.</b> A deployment whose environment is already busy is put back on
 *       the queue rather than racing the deployment that holds it. Ordering is preserved for a given
 *       environment, which is what "one active deployment per environment" has to mean.
 *   <li><b>Crash reconciliation.</b> On startup, deployments left in a non terminal state by a previous
 *       process are failed with an explicit message instead of hanging in "Building" forever.
 * </ul>
 */
@Component
public class DeploymentWorker {

    private static final Logger log = LoggerFactory.getLogger(DeploymentWorker.class);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration BUSY_BACKOFF = Duration.ofSeconds(2);

    private final DeploymentQueue queue;
    private final DeploymentContextFactory contextFactory;
    private final DeploymentPipeline pipeline;
    private final DeploymentStateService stateService;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentLogService logService;
    private final DeployForgeProperties properties;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<Thread> workers = new java.util.concurrent.CopyOnWriteArrayList<>();

    public DeploymentWorker(
            DeploymentQueue queue,
            DeploymentContextFactory contextFactory,
            DeploymentPipeline pipeline,
            DeploymentStateService stateService,
            DeploymentRepository deploymentRepository,
            DeploymentLogService logService,
            DeployForgeProperties properties) {
        this.queue = queue;
        this.contextFactory = contextFactory;
        this.pipeline = pipeline;
        this.stateService = stateService;
        this.deploymentRepository = deploymentRepository;
        this.logService = logService;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        reconcileInterruptedDeployments();

        int concurrency = properties.deployment().workerConcurrency();
        for (int index = 0; index < concurrency; index++) {
            Thread worker = new Thread(this::loop, "df-worker-" + index);
            worker.setDaemon(true);
            worker.start();
            workers.add(worker);
        }
        log.info("deployment_workers_started count={}", concurrency);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        workers.forEach(Thread::interrupt);
        log.info("deployment_workers_stopping count={}", workers.size());
    }

    private void loop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Optional<UUID> next = queue.poll(POLL_TIMEOUT);
                if (next.isEmpty()) {
                    continue;
                }
                process(next.get());
            } catch (RuntimeException e) {
                // A worker thread must survive anything a single deployment can do to it.
                log.error("deployment_worker_error", e);
                sleep(BUSY_BACKOFF);
            }
        }
    }

    private void process(UUID deploymentId) {
        Optional<Deployment> maybeDeployment = deploymentRepository.findById(deploymentId);
        if (maybeDeployment.isEmpty()) {
            log.warn("deployment_missing_skipped deployment={}", deploymentId);
            return;
        }
        Deployment deployment = maybeDeployment.get();

        if (deployment.getStatus() != DeploymentStatus.QUEUED) {
            log.info(
                    "deployment_not_queued_skipped deployment={} status={}",
                    deploymentId,
                    deployment.getStatus());
            return;
        }
        if (queue.isCancellationRequested(deploymentId)) {
            stateService.cancelRemainingSteps(deploymentId);
            stateService.markCancelled(deploymentId);
            queue.clearCancellation(deploymentId);
            return;
        }

        UUID environmentId = deployment.getEnvironmentId();
        Duration lockTtl = properties.deployment().buildTimeout().plus(Duration.ofMinutes(10));
        if (!queue.tryAcquireEnvironmentLock(environmentId, deploymentId, lockTtl)) {
            Optional<UUID> holder = queue.environmentLockHolder(environmentId);
            log.debug(
                    "environment_busy deployment={} environment={} holder={}",
                    deploymentId,
                    environmentId,
                    holder.orElse(null));
            queue.requeue(deploymentId);
            sleep(BUSY_BACKOFF);
            return;
        }

        try {
            DeploymentContext context = contextFactory.create(deploymentId);
            pipeline.run(context);
        } finally {
            queue.releaseEnvironmentLock(environmentId, deploymentId);
            queue.clearCancellation(deploymentId);
        }
    }

    /**
     * Fails deployments that a previous process left mid-flight.
     *
     * <p>Their containers, if any, are not touched here: the reaper handles orphaned containers, and a
     * half-started container is safer to inspect than to silently destroy at boot.
     */
    @Transactional
    public void reconcileInterruptedDeployments() {
        List<Deployment> active = deploymentRepository.findAllActive();
        for (Deployment deployment : active) {
            if (deployment.getStatus() == DeploymentStatus.QUEUED) {
                // Still legitimately waiting: put it back on the queue after a restart.
                queue.enqueue(deployment.getId());
                continue;
            }
            logService.append(
                    deployment.getId(),
                    LogLevel.ERROR,
                    LogSource.SYSTEM,
                    "DeployForge restarted while this deployment was running, so it was marked as failed.");
            stateService.markFailed(
                    deployment.getId(),
                    "Interrupted by a DeployForge restart while in " + deployment.getStatus(),
                    null);
            log.warn(
                    "deployment_reconciled deployment={} previous_status={}",
                    deployment.getId(),
                    deployment.getStatus());
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
