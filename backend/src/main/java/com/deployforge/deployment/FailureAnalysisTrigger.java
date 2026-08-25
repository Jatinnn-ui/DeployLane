package com.deployforge.deployment;

import java.util.UUID;

/**
 * Port the pipeline uses to hand a failed deployment to the AI analyzer.
 *
 * <p>Declared in the deployment module and implemented in the AI module so the pipeline never depends
 * on an AI provider. Implementations must return immediately and do their work asynchronously - a slow
 * or unavailable model must not delay the deployment being marked failed.
 */
public interface FailureAnalysisTrigger {

    void onDeploymentFailed(UUID deploymentId);
}
