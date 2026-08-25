package com.deployforge.deployment;

import com.deployforge.common.error.Exceptions.NotFoundException;
import com.deployforge.deployment.event.DeploymentEvent;
import com.deployforge.deployment.event.DeploymentEventPublisher;
import com.deployforge.log.DeploymentLogService;
import com.deployforge.log.LogLevel;
import com.deployforge.log.LogSource;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Short, explicit write transactions for deployment progress.
 *
 * <p>The pipeline runs for minutes; every mutation it makes goes through one of these methods so that
 * a transaction is open for milliseconds at a time and never across a clone, a build or an HTTP probe.
 *
 * <p>Each method also emits the matching real-time event, which keeps "persisted state" and "what the
 * browser sees" from drifting apart.
 */
@Service
public class DeploymentStateService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentStateService.class);

    private final DeploymentRepository deploymentRepository;
    private final DeploymentStepRepository stepRepository;
    private final DeploymentStateMachine stateMachine;
    private final DeploymentLogService logService;
    private final ObjectProvider<DeploymentEventPublisher> eventPublisher;

    public DeploymentStateService(
            DeploymentRepository deploymentRepository,
            DeploymentStepRepository stepRepository,
            DeploymentStateMachine stateMachine,
            DeploymentLogService logService,
            ObjectProvider<DeploymentEventPublisher> eventPublisher) {
        this.deploymentRepository = deploymentRepository;
        this.stepRepository = stepRepository;
        this.stateMachine = stateMachine;
        this.logService = logService;
        this.eventPublisher = eventPublisher;
    }

    // ------------------------------------------------------------------ steps

    /** Creates the full set of timeline rows up-front so the UI can render the plan immediately. */
    @Transactional
    public void initializeSteps(UUID deploymentId, boolean imageReuse) {
        for (DeploymentStepName name : DeploymentStepName.values()) {
            if (stepRepository.findByDeploymentIdAndStep(deploymentId, name).isPresent()) {
                continue;
            }
            stepRepository.save(new DeploymentStep(deploymentId, name, name.sequence()));
        }
        DeploymentStep queue =
                stepRepository
                        .findByDeploymentIdAndStep(deploymentId, DeploymentStepName.QUEUE)
                        .orElseThrow(() -> NotFoundException.of("Deployment step", DeploymentStepName.QUEUE));
        queue.start();
        queue.succeed(imageReuse ? "Queued for rollback" : "Queued");
        publishStep(deploymentId, queue);
    }

    @Transactional
    public void startStep(UUID deploymentId, DeploymentStepName name) {
        DeploymentStep step = requireStep(deploymentId, name);
        step.start();
        publishStep(deploymentId, step);
    }

    @Transactional
    public void completeStep(UUID deploymentId, DeploymentStepName name, String detail) {
        DeploymentStep step = requireStep(deploymentId, name);
        step.succeed(detail);
        publishStep(deploymentId, step);
    }

    @Transactional
    public void failStep(UUID deploymentId, DeploymentStepName name, String detail) {
        DeploymentStep step = requireStep(deploymentId, name);
        step.fail(detail);
        publishStep(deploymentId, step);
    }

    @Transactional
    public void skipStep(UUID deploymentId, DeploymentStepName name, String detail) {
        DeploymentStep step = requireStep(deploymentId, name);
        step.skip(detail);
        publishStep(deploymentId, step);
    }

    @Transactional
    public void cancelRemainingSteps(UUID deploymentId) {
        for (DeploymentStep step : stepRepository.findByDeploymentIdOrderBySequenceNumberAsc(deploymentId)) {
            if (step.getStatus() == DeploymentStep.StepStatus.PENDING
                    || step.getStatus() == DeploymentStep.StepStatus.RUNNING) {
                step.cancel();
                publishStep(deploymentId, step);
            }
        }
    }

    // ------------------------------------------------------------------ status

    @Transactional
    public void transition(UUID deploymentId, DeploymentStatus target) {
        Deployment deployment = require(deploymentId);
        stateMachine.transition(deployment, target);
        log.info("deployment_status deployment={} status={}", deploymentId, target);
        publish(
                new DeploymentEvent.StatusChanged(
                        deploymentId, target.name(), target.label(), deployment.getDeploymentNumber()));
        logService.append(
                deploymentId,
                LogLevel.INFO,
                LogSource.SYSTEM,
                switch (target) {
                    case CLONING -> "Cloning repository...";
                    case DETECTING -> "Detecting framework...";
                    case BUILDING -> "Preparing build...";
                    case IMAGE_BUILDING -> "Building application image...";
                    case STARTING -> "Starting container...";
                    case HEALTH_CHECKING -> "Running health check...";
                    case READY -> "Deployment ready.";
                    case FAILED -> "Deployment failed.";
                    case CANCELLED -> "Deployment cancelled.";
                    case STOPPED -> "Deployment stopped.";
                    case QUEUED -> "Deployment queued.";
                });
    }

    /**
     * Marks a deployment failed and records the stage and message.
     *
     * <p>Runs in its own transaction ({@code REQUIRES_NEW}) so that recording a failure cannot itself be
     * rolled back by whatever went wrong.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID deploymentId, String message, Integer exitCode) {
        Deployment deployment = require(deploymentId);
        DeploymentStatus stage = deployment.getStatus();
        if (deployment.getStatus().isTerminal()) {
            return;
        }
        stateMachine.transition(deployment, DeploymentStatus.FAILED);
        deployment.markFailure(stage, message, exitCode);
        log.warn("deployment_failed deployment={} stage={} message={}", deploymentId, stage, message);
        publish(new DeploymentEvent.DeploymentFailed(deploymentId, stage.name(), message, exitCode));
        publish(
                new DeploymentEvent.StatusChanged(
                        deploymentId,
                        DeploymentStatus.FAILED.name(),
                        DeploymentStatus.FAILED.label(),
                        deployment.getDeploymentNumber()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCancelled(UUID deploymentId) {
        Deployment deployment = require(deploymentId);
        if (deployment.getStatus().isTerminal()) {
            return;
        }
        stateMachine.transition(deployment, DeploymentStatus.CANCELLED);
        publish(
                new DeploymentEvent.StatusChanged(
                        deploymentId,
                        DeploymentStatus.CANCELLED.name(),
                        DeploymentStatus.CANCELLED.label(),
                        deployment.getDeploymentNumber()));
    }

    @Transactional
    public void markStopped(UUID deploymentId) {
        Deployment deployment = require(deploymentId);
        if (deployment.getStatus() == DeploymentStatus.READY) {
            stateMachine.transition(deployment, DeploymentStatus.STOPPED);
            deployment.setPromoted(false);
            publish(
                    new DeploymentEvent.StatusChanged(
                            deploymentId,
                            DeploymentStatus.STOPPED.name(),
                            DeploymentStatus.STOPPED.label(),
                            deployment.getDeploymentNumber()));
        }
    }

    @Transactional
    public void requestCancellation(UUID deploymentId) {
        require(deploymentId).setCancellationRequested(true);
    }

    // ------------------------------------------------------------------ details

    @Transactional
    public void recordCommit(UUID deploymentId, String sha, String message, String author) {
        Deployment deployment = require(deploymentId);
        deployment.setCommitSha(sha);
        deployment.setCommitMessage(message);
        deployment.setCommitAuthor(author);
    }

    @Transactional
    public void recordDetection(
            UUID deploymentId,
            com.deployforge.detection.Framework framework,
            com.deployforge.detection.RuntimeType runtime,
            String installCommand,
            String buildCommand,
            String startCommand,
            Integer containerPort) {
        Deployment deployment = require(deploymentId);
        deployment.setFramework(framework);
        deployment.setRuntime(runtime);
        deployment.setInstallCommand(installCommand);
        deployment.setBuildCommand(buildCommand);
        deployment.setStartCommand(startCommand);
        deployment.setContainerPort(containerPort);
    }

    @Transactional
    public void recordDockerfile(UUID deploymentId, String dockerfilePath, String generatedContent) {
        Deployment deployment = require(deploymentId);
        deployment.setDockerfilePath(dockerfilePath);
        if (generatedContent != null) {
            deployment.setGeneratedDockerfile(generatedContent);
        }
    }

    @Transactional
    public void recordImage(UUID deploymentId, String imageTag) {
        require(deploymentId).setImageTag(imageTag);
    }

    @Transactional
    public void recordContainer(UUID deploymentId, String containerId, Integer hostPort) {
        Deployment deployment = require(deploymentId);
        deployment.setContainerId(containerId);
        deployment.setHostPort(hostPort);
    }

    @Transactional
    public void recordUrl(UUID deploymentId, String url) {
        require(deploymentId).setDeploymentUrl(url);
    }

    @Transactional
    public void markReady(UUID deploymentId, String url) {
        Deployment deployment = require(deploymentId);
        stateMachine.transition(deployment, DeploymentStatus.READY);
        deployment.setDeploymentUrl(url);
        deployment.setPromoted(true);
        publish(
                new DeploymentEvent.StatusChanged(
                        deploymentId,
                        DeploymentStatus.READY.name(),
                        DeploymentStatus.READY.label(),
                        deployment.getDeploymentNumber()));
        publish(new DeploymentEvent.DeploymentReady(deploymentId, url, deployment.getDurationMs()));
        log.info("deployment_ready deployment={} url={} duration_ms={}", deploymentId, url, deployment.getDurationMs());
    }

    @Transactional(readOnly = true)
    public Deployment load(UUID deploymentId) {
        return require(deploymentId);
    }

    @Transactional(readOnly = true)
    public boolean isCancellationRequested(UUID deploymentId) {
        return deploymentRepository
                .findById(deploymentId)
                .map(Deployment::isCancellationRequested)
                .orElse(false);
    }

    private Deployment require(UUID deploymentId) {
        return deploymentRepository
                .findById(deploymentId)
                .orElseThrow(() -> NotFoundException.of("Deployment", deploymentId));
    }

    private DeploymentStep requireStep(UUID deploymentId, DeploymentStepName name) {
        return stepRepository
                .findByDeploymentIdAndStep(deploymentId, name)
                .orElseGet(
                        () -> stepRepository.save(new DeploymentStep(deploymentId, name, name.sequence())));
    }

    private void publishStep(UUID deploymentId, DeploymentStep step) {
        publish(
                new DeploymentEvent.StepUpdated(
                        deploymentId,
                        step.getStep().name(),
                        step.getStep().label(),
                        step.getStatus().name(),
                        step.getDurationMs(),
                        step.getDetail()));
    }

    private void publish(DeploymentEvent event) {
        DeploymentEventPublisher publisher = eventPublisher.getIfAvailable();
        if (publisher == null) {
            return;
        }
        try {
            publisher.publish(event);
        } catch (RuntimeException e) {
            log.debug("event_publish_failed deployment={} reason={}", event.deploymentId(), e.getMessage());
        }
    }
}
