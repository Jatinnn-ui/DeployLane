package com.deployforge.ai;

import com.deployforge.ai.AiModels.AiAnalysisContext;
import com.deployforge.ai.AiModels.AiAnalysisResult;
import com.deployforge.ai.AiModels.AiChatContext;
import com.deployforge.ai.AiModels.AiChatResult;

/**
 * The seam between DeployForge and any AI vendor.
 *
 * <p>Nothing outside this package imports a provider SDK or knows which model is configured. Swapping
 * Gemini for OpenAI, or adding a self hosted model, is a new implementation of this interface plus one
 * configuration value.
 *
 * <p>Implementations must:
 *
 * <ul>
 *   <li>never receive or request secret <em>values</em> - the context carries variable names only
 *   <li>return a validated {@link AiAnalysisResult}; malformed model output is a provider level problem,
 *       not something the UI should have to defend against
 *   <li>fail fast and cheaply when unconfigured, so the platform can fall back
 * </ul>
 */
public interface AiProvider {

    /** Stable identifier stored alongside the analysis, e.g. {@code gemini}. */
    String name();

    /** False when required credentials are missing; the registry then falls back. */
    boolean isConfigured();

    /**
     * Explains a failed deployment.
     *
     * @throws AiProviderException when the provider is unreachable or returns unusable output
     */
    AiAnalysisResult analyzeDeploymentFailure(AiAnalysisContext context);

    /** Answers a question about a project using only the supplied, pre-gathered context. */
    AiChatResult chat(AiChatContext context);
}
