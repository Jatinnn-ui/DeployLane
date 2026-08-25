package com.deployforge.ai;

import com.deployforge.deployment.FailureAnalysisTrigger;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter that lets the deployment pipeline request AI analysis without depending on the AI module.
 *
 * <p>Returns immediately; the analysis itself runs on its own thread.
 */
@Component
public class AiFailureAnalysisTrigger implements FailureAnalysisTrigger {

    private static final Logger log = LoggerFactory.getLogger(AiFailureAnalysisTrigger.class);

    private final DeploymentAnalysisService analysisService;

    public AiFailureAnalysisTrigger(DeploymentAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Override
    public void onDeploymentFailed(UUID deploymentId) {
        try {
            analysisService.analyzeAsync(deploymentId);
        } catch (RuntimeException e) {
            // The deployment is already marked failed; analysis is a bonus, never a blocker.
            log.warn("analysis_trigger_failed deployment={} reason={}", deploymentId, e.getMessage());
        }
    }
}
