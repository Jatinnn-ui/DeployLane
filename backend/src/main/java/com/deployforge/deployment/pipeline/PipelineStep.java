package com.deployforge.deployment.pipeline;

import com.deployforge.deployment.DeploymentStepName;

/**
 * One stage of the deployment pipeline.
 *
 * <p>Contract:
 *
 * <ul>
 *   <li>throw {@link StepFailedException} for an expected, explainable failure (build error, health
 *       check timeout). The orchestrator turns it into a {@code FAILED} deployment with a real message.
 *   <li>throw {@link StepCancelledException} when the user cancelled mid-flight.
 *   <li>never swallow an exception, and never return normally after failing.
 * </ul>
 */
public interface PipelineStep {

    DeploymentStepName name();

    /** False when the step does not apply to this deployment, e.g. cloning during a rollback. */
    default boolean applies(DeploymentContext context) {
        return true;
    }

    /** Reason shown on the timeline when {@link #applies} is false. */
    default String skipReason(DeploymentContext context) {
        return "Not required for this deployment";
    }

    void execute(DeploymentContext context);
}
