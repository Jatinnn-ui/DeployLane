package com.deployforge.ai;

import com.deployforge.ai.dto.AiDtos.AiStatusResponse;
import com.deployforge.ai.dto.AiDtos.AnalysisResponse;
import com.deployforge.ai.dto.AiDtos.ChatRequest;
import com.deployforge.ai.dto.AiDtos.ChatResponse;
import com.deployforge.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI endpoints: failure analysis and the project scoped DevOps chat.
 *
 * <p>Both require the {@code USE_AI} permission to <em>invoke</em>; reading an existing analysis only needs
 * {@code VIEW_ANALYSIS}, so viewers can see why something broke without being able to spend AI quota.
 */
@RestController
@Tag(name = "AI", description = "Deployment failure analysis and the DevOps assistant")
public class AiController {

    private final DeploymentAnalysisService analysisService;
    private final AiChatService chatService;
    private final AiProviderRegistry providerRegistry;

    public AiController(
            DeploymentAnalysisService analysisService,
            AiChatService chatService,
            AiProviderRegistry providerRegistry) {
        this.analysisService = analysisService;
        this.chatService = chatService;
        this.providerRegistry = providerRegistry;
    }

    @GetMapping("/api/v1/ai/status")
    @Operation(
            summary = "Which analyzer is active",
            description =
                    "Reports the configured provider and the one actually in use. They differ when an API key is "
                            + "missing and the local rule based analyzer has taken over.")
    public AiStatusResponse status(@AuthenticationPrincipal AuthenticatedUser user) {
        return new AiStatusResponse(
                providerRegistry.configuredProviderName(),
                providerRegistry.primary().name(),
                providerRegistry.externalProviderActive());
    }

    @GetMapping("/api/v1/deployments/{deploymentId}/analysis")
    @Operation(summary = "Read the stored failure analysis")
    public AnalysisResponse getAnalysis(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID deploymentId) {
        return analysisService.get(deploymentId, user.userId());
    }

    @PostMapping("/api/v1/deployments/{deploymentId}/analysis")
    @Operation(
            summary = "Analyse a failed deployment",
            description =
                    "Returns the cached analysis unless force=true. Only FAILED deployments can be analysed. "
                            + "Rate limited per user.")
    public AnalysisResponse analyze(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID deploymentId,
            @RequestParam(name = "force", defaultValue = "false") boolean force) {
        return analysisService.analyzeOnDemand(deploymentId, user.userId(), force);
    }

    @PostMapping("/api/v1/projects/{projectId}/ai/chat")
    @Operation(
            summary = "Ask about this project",
            description =
                    "The backend gathers context with read-only tools (deployments, logs, health, container stats) "
                            + "and passes it to the model. The model cannot query anything or run commands.")
    public ChatResponse chat(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId,
            @Valid @RequestBody ChatRequest request) {
        return chatService.chat(projectId, user.userId(), request);
    }

    @GetMapping("/api/v1/ai/tools")
    @Operation(summary = "The read-only tools the assistant may use")
    public List<String> tools(@AuthenticationPrincipal AuthenticatedUser user) {
        return chatService.availableTools();
    }
}
