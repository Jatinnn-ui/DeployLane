package com.deployforge.ai.dto;

import com.deployforge.ai.AiModels.Severity;
import com.deployforge.ai.AiModels.SuggestedFix;
import com.deployforge.ai.DeploymentAnalysis.AnalysisStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** AI API payloads. */
public final class AiDtos {

    private AiDtos() {}

    public record AnalysisResponse(
            UUID deploymentId,
            AnalysisStatus status,
            String summary,
            String rootCause,
            List<String> evidence,
            List<SuggestedFix> suggestedFixes,
            Severity severity,
            Double confidence,
            String provider,
            String model,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt) {

        public static AnalysisResponse unavailable(UUID deploymentId, String reason) {
            return new AnalysisResponse(
                    deploymentId,
                    AnalysisStatus.UNAVAILABLE,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    reason,
                    null,
                    null);
        }

        public static AnalysisResponse pending(UUID deploymentId) {
            return new AnalysisResponse(
                    deploymentId,
                    AnalysisStatus.PENDING,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }
    }

    public record ChatRequest(
            @NotBlank @Size(max = 2000) String message,
            @Size(max = 20) List<ChatTurn> history,
            UUID deploymentId) {}

    public record ChatTurn(@NotBlank @Size(max = 20) String role, @NotBlank @Size(max = 4000) String content) {}

    public record ChatResponse(
            String answer,
            String provider,
            String model,
            List<String> usedTools,
            boolean externalProvider,
            Instant answeredAt) {}

    public record AiStatusResponse(
            String configuredProvider, String activeProvider, boolean externalProviderActive) {}
}
