package com.deployforge.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Value types exchanged with an {@link AiProvider}.
 *
 * <p>The context is assembled by {@code DeploymentAnalysisService} and is the <em>only</em> thing an AI
 * provider ever sees. Note what it carries about secrets: {@code environmentVariableNames} - names, never
 * values. Log lines have already been through {@code SecretRedactionService} on the way into storage.
 */
public final class AiModels {

    private AiModels() {}

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /** Evidence handed to the model for a failed deployment. */
    public record AiAnalysisContext(
            String projectName,
            String framework,
            String runtime,
            String failureStage,
            String failureMessage,
            Integer exitCode,
            String installCommand,
            String buildCommand,
            String startCommand,
            Integer containerPort,
            String healthCheckPath,
            String branch,
            String commitSha,
            String dockerfileSource,
            boolean dockerfileGenerated,
            List<String> environmentVariableNames,
            List<String> recentLogs,
            List<String> errorLogs) {}

    public record SuggestedFix(String title, String description, String command) {}

    /** Structured, validated analysis. Never surfaced to the UI without passing the parser first. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiAnalysisResult(
            String summary,
            String rootCause,
            List<String> evidence,
            List<SuggestedFix> suggestedFixes,
            Severity severity,
            double confidence,
            String provider,
            String model,
            String rawResponse) {

        public AiAnalysisResult withProvider(String provider, String model, String rawResponse) {
            return new AiAnalysisResult(
                    summary, rootCause, evidence, suggestedFixes, severity, confidence, provider, model, rawResponse);
        }
    }

    /** One turn of the DevOps chat. */
    public record ChatMessage(String role, String content) {}

    /**
     * Chat request context.
     *
     * <p>{@code projectContext} is a pre-rendered, redacted summary produced by the backend's own tools -
     * the model cannot query anything itself.
     */
    public record AiChatContext(
            String projectName, String projectContext, List<ChatMessage> history, String question) {}

    public record AiChatResult(String answer, String provider, String model, List<String> usedTools) {}
}
