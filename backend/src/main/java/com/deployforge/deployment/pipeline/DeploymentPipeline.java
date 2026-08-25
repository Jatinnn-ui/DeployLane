package com.deployforge.deployment.pipeline;

import com.deployforge.activity.ActivityAction;
import com.deployforge.activity.ActivityService;
import com.deployforge.common.util.SafePaths;
import com.deployforge.deployment.DeploymentStateService;
import com.deployforge.deployment.DeploymentStepName;
import com.deployforge.deployment.DeploymentTriggerType;
import com.deployforge.deployment.FailureAnalysisTrigger;
import com.deployforge.log.DeploymentLogService;
import com.deployforge.log.LogLevel;
import com.deployforge.log.LogSource;
import com.deployforge.notification.NotificationService;
import com.deployforge.notification.NotificationType;
import com.deployforge.runtime.DeploymentRuntime;
import com.deployforge.runtime.PortAllocator;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Runs the deployment steps in order and owns every outcome.
 *
 * <p>Responsibilities kept in one place on purpose:
 *
 * <ul>
 *   <li>advance the deployment status before each step, through {@code DeploymentStateMachine}
 *   <li>mark the timeline row running / succeeded / failed / skipped
 *   <li>translate a step failure into a {@code FAILED} deployment with a real message, then release the
 *       resources that step had already created
 *   <li>kick off AI failure analysis and notify the workspace
 * </ul>
 *
 * <p>Steps themselves never touch status, notifications or cleanup - they do one job and throw a typed
 * exception when it cannot be done.
 */
@Component
public class DeploymentPipeline {

    private static final Logger log = LoggerFactory.getLogger(DeploymentPipeline.class);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(5);

    private final List<PipelineStep> steps;
    private final DeploymentStateService stateService;
    private final DeploymentLogService logService;
    private final DeploymentRuntime runtime;
    private final PortAllocator portAllocator;
    private final NotificationService notificationService;
    private final ActivityService activityService;
    private final ObjectProvider<FailureAnalysisTrigger> failureAnalysis;

    public DeploymentPipeline(
            List<PipelineStep> steps,
            DeploymentStateService stateService,
            DeploymentLogService logService,
            DeploymentRuntime runtime,
            PortAllocator portAllocator,
            NotificationService notificationService,
            ActivityService activityService,
            ObjectProvider<FailureAnalysisTrigger> failureAnalysis) {
        // Order is defined by the enum, not by bean discovery order or an @Order annotation.
        this.steps =
                steps.stream()
                        .sorted(Comparator.comparingInt(step -> step.name().sequence()))
                        .toList();
        this.stateService = stateService;
        this.logService = logService;
        this.runtime = runtime;
        this.portAllocator = portAllocator;
        this.notificationService = notificationService;
        this.activityService = activityService;
        this.failureAnalysis = failureAnalysis;
    }

    public void run(DeploymentContext context) {
        stateService.initializeSteps(context.getDeploymentId(), context.isImageReuse());
        logService.system(
                context.getDeploymentId(),
                "Deployment #"
                        + context.getDeploymentNumber()
                        + " started ("
                        + context.getTriggerType()
                        + ") for "
                        + context.repositoryFullName()
                        + "@"
                        + context.getBranch());

        try {
            for (PipelineStep step : steps) {
                if (step.name() == DeploymentStepName.QUEUE) {
                    continue;
                }
                if (context.isCancelled()) {
                    throw new StepCancelledException("Cancelled before " + step.name().label());
                }
                if (!step.applies(context)) {
                    stateService.skipStep(
                            context.getDeploymentId(), step.name(), step.skipReason(context));
                    continue;
                }
                // FINALIZE marks the deployment READY itself, at the same moment traffic switches.
                if (step.name() != DeploymentStepName.FINALIZE) {
                    stateService.transition(context.getDeploymentId(), step.name().status());
                }
                stateService.startStep(context.getDeploymentId(), step.name());
                step.execute(context);
                stateService.completeStep(
                        context.getDeploymentId(), step.name(), stepDetail(context, step));
            }
            onSuccess(context);

        } catch (StepCancelledException e) {
            onCancelled(context, e.getMessage());
        } catch (StepFailedException e) {
            onFailed(context, e.getMessage(), e.getRemediation(), e.getExitCode());
        } catch (RuntimeException e) {
            // Unexpected: log with a stack trace for us, show a request-id style message to the user.
            log.error("deployment_pipeline_crashed deployment={}", context.getDeploymentId(), e);
            onFailed(
                    context,
                    "The deployment failed unexpectedly: " + shortMessage(e),
                    "This is a DeployForge side error rather than a problem with your project. The platform logs hold the stack trace.",
                    null);
        } finally {
            logService.clearSecrets(context.getDeploymentId());
        }
    }

    // ------------------------------------------------------------------ outcomes

    private void onSuccess(DeploymentContext context) {
        stateService.completeStep(
                context.getDeploymentId(), DeploymentStepName.FINALIZE, "Live at " + context.getDeploymentUrl());

        activityService.record(
                context.getWorkspaceId(),
                context.getProjectId(),
                null,
                null,
                context.getTriggerType() == DeploymentTriggerType.ROLLBACK
                        ? ActivityAction.DEPLOYMENT_ROLLED_BACK
                        : ActivityAction.DEPLOYMENT_SUCCEEDED,
                "DEPLOYMENT",
                context.getDeploymentId().toString(),
                Map.of(
                        "deploymentNumber", context.getDeploymentNumber(),
                        "url", String.valueOf(context.getDeploymentUrl()),
                        "commit", String.valueOf(context.getCommitSha())));

        notificationService.notifyWorkspace(
                context.getWorkspaceId(),
                context.getTriggerType() == DeploymentTriggerType.ROLLBACK
                        ? NotificationType.ROLLBACK_COMPLETED
                        : NotificationType.DEPLOYMENT_SUCCESS,
                context.getProjectName() + " deployment #" + context.getDeploymentNumber() + " is live",
                context.getDeploymentUrl(),
                context.getProjectId(),
                context.getDeploymentId());

        log.info(
                "deployment_succeeded deployment={} project={} url={}",
                context.getDeploymentId(),
                context.getProjectId(),
                context.getDeploymentUrl());
    }

    private void onCancelled(DeploymentContext context, String reason) {
        logService.append(
                context.getDeploymentId(),
                LogLevel.WARN,
                LogSource.SYSTEM,
                "Deployment cancelled: " + reason);
        releasePartialResources(context);
        stateService.cancelRemainingSteps(context.getDeploymentId());
        stateService.markCancelled(context.getDeploymentId());

        activityService.record(
                context.getWorkspaceId(),
                context.getProjectId(),
                null,
                null,
                ActivityAction.DEPLOYMENT_CANCELLED,
                "DEPLOYMENT",
                context.getDeploymentId().toString(),
                Map.of("deploymentNumber", context.getDeploymentNumber()));

        notificationService.notifyWorkspace(
                context.getWorkspaceId(),
                NotificationType.DEPLOYMENT_CANCELLED,
                context.getProjectName() + " deployment #" + context.getDeploymentNumber() + " was cancelled",
                reason,
                context.getProjectId(),
                context.getDeploymentId());
    }

    private void onFailed(
            DeploymentContext context, String message, String remediation, Integer exitCode) {
        DeploymentStepName failedStep = currentStep(context);
        stateService.failStep(context.getDeploymentId(), failedStep, message);

        logService.append(context.getDeploymentId(), LogLevel.ERROR, LogSource.SYSTEM, message);
        if (remediation != null) {
            logService.append(
                    context.getDeploymentId(), LogLevel.WARN, LogSource.SYSTEM, "Hint: " + remediation);
        }

        stateService.markFailed(context.getDeploymentId(), message, exitCode);
        // The old deployment is untouched and still serving: a failed deploy is not an outage.
        releasePartialResources(context);

        activityService.record(
                context.getWorkspaceId(),
                context.getProjectId(),
                null,
                null,
                ActivityAction.DEPLOYMENT_FAILED,
                "DEPLOYMENT",
                context.getDeploymentId().toString(),
                Map.of(
                        "deploymentNumber", context.getDeploymentNumber(),
                        "stage", failedStep.name(),
                        "message", message));

        notificationService.notifyWorkspace(
                context.getWorkspaceId(),
                NotificationType.DEPLOYMENT_FAILED,
                context.getProjectName() + " deployment #" + context.getDeploymentNumber() + " failed",
                message,
                context.getProjectId(),
                context.getDeploymentId());

        // Analysis runs after the logs are persisted, so the analyzer sees the real evidence.
        failureAnalysis.ifAvailable(trigger -> trigger.onDeploymentFailed(context.getDeploymentId()));
    }

    /**
     * Releases only what this deployment created.
     *
     * <p>The container is removed (it is not serving anything), the port lease is dropped, and the
     * checkout is kept for the retention window because it is failure evidence. The image is left to the
     * cleanup job so a rollback target is not destroyed by a later failure.
     */
    private void releasePartialResources(DeploymentContext context) {
        if (context.getContainerId() != null) {
            runtime.stop(context.getContainerId(), STOP_TIMEOUT);
            runtime.remove(context.getContainerId());
        }
        if (context.getHostPort() != null) {
            portAllocator.releaseLease(context.getHostPort());
        }
        if (context.isCancelled() && context.getWorkDirectory() != null) {
            SafePaths.deleteQuietly(context.getWorkDirectory());
        }
    }

    /** Best guess at which step was running, used to attribute the failure on the timeline. */
    private DeploymentStepName currentStep(DeploymentContext context) {
        if (context.getDeploymentUrl() != null) {
            return DeploymentStepName.FINALIZE;
        }
        if (context.getContainerId() != null) {
            return DeploymentStepName.HEALTH_CHECK;
        }
        if (context.getImageTag() != null) {
            return DeploymentStepName.CONTAINER_START;
        }
        if (context.getDockerfile() != null) {
            return DeploymentStepName.IMAGE_BUILD;
        }
        if (context.getFramework() != null) {
            return DeploymentStepName.BUILD;
        }
        if (context.getCommitSha() != null) {
            return DeploymentStepName.DETECT;
        }
        return DeploymentStepName.CLONE;
    }

    private String stepDetail(DeploymentContext context, PipelineStep step) {
        return switch (step.name()) {
            case CLONE ->
                    context.getCommitSha() == null
                            ? null
                            : context.getCommitSha().substring(0, 7) + " " + context.getCommitMessage();
            case DETECT ->
                    context.getFramework() == null ? null : context.getFramework().displayName();
            case BUILD ->
                    context.getGeneratedDockerfile() != null
                            ? "Generated Dockerfile"
                            : "Using committed Dockerfile";
            case IMAGE_BUILD -> context.getImageTag();
            case CONTAINER_START -> context.containerName() + " on port " + context.getHostPort();
            case HEALTH_CHECK -> "Healthy";
            case FINALIZE -> context.getDeploymentUrl();
            case QUEUE -> null;
        };
    }

    private String shortMessage(RuntimeException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) + "..." : message;
    }
}
