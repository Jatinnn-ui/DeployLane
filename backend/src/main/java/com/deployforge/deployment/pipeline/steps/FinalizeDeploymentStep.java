package com.deployforge.deployment.pipeline.steps;

import com.deployforge.common.util.SafePaths;
import com.deployforge.deployment.DeploymentPromotionService;
import com.deployforge.deployment.DeploymentStepName;
import com.deployforge.deployment.pipeline.DeploymentContext;
import com.deployforge.deployment.pipeline.PipelineStep;
import com.deployforge.deployment.pipeline.StepFailedException;
import com.deployforge.log.DeploymentLogService;
import com.deployforge.log.LogLevel;
import com.deployforge.log.LogSource;
import org.springframework.stereotype.Component;

/**
 * Last stage: route traffic to the new container, retire the previous one, tidy up.
 *
 * <p>Marking the deployment {@code READY} happens inside the promotion, not here, because "ready" and
 * "receiving traffic" must become true at the same moment.
 */
@Component
public class FinalizeDeploymentStep implements PipelineStep {

    private final DeploymentPromotionService promotionService;
    private final DeploymentLogService logService;

    public FinalizeDeploymentStep(
            DeploymentPromotionService promotionService, DeploymentLogService logService) {
        this.promotionService = promotionService;
        this.logService = logService;
    }

    @Override
    public DeploymentStepName name() {
        return DeploymentStepName.FINALIZE;
    }

    @Override
    public void execute(DeploymentContext context) {
        if (context.getHostPort() == null) {
            throw new StepFailedException("The deployment has no published port to route traffic to");
        }

        String url =
                promotionService.promote(
                        context.getProjectId(),
                        context.getEnvironmentId(),
                        context.getDeploymentId(),
                        context.getProjectSlug(),
                        context.getHostPort());
        context.setDeploymentUrl(url);

        logService.append(
                context.getDeploymentId(), LogLevel.INFO, LogSource.SYSTEM, "Deployment live at " + url);

        // The checkout is only needed for a build. Successful deployments release it immediately;
        // failed ones keep theirs until the retention window so the evidence stays inspectable.
        if (context.getWorkDirectory() != null) {
            SafePaths.deleteQuietly(context.getWorkDirectory());
        }
    }
}
