package com.deployforge.ai;

import com.deployforge.ai.AiModels.AiChatContext;
import com.deployforge.ai.AiModels.AiChatResult;
import com.deployforge.ai.AiModels.ChatMessage;
import com.deployforge.ai.dto.AiDtos.ChatRequest;
import com.deployforge.ai.dto.AiDtos.ChatResponse;
import com.deployforge.ai.dto.AiDtos.ChatTurn;
import com.deployforge.config.DeployForgeProperties;
import com.deployforge.project.Project;
import com.deployforge.project.ProjectAccessGuard;
import com.deployforge.security.RateLimitService;
import com.deployforge.security.SecretRedactionService;
import com.deployforge.workspace.Permission;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project aware DevOps chat.
 *
 * <p>The flow is: authorize, gather context with the controlled read-only tools, ask the provider, redact the
 * answer, return it together with the list of tools that were used.
 *
 * <p>What this deliberately does not do: give the model any ability to act. There is no tool that deploys,
 * restarts, deletes, rotates a secret or runs a shell command. The model can recommend such actions in
 * prose; performing them always requires the user to press a button in the UI.
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);
    private static final int MAX_HISTORY_TURNS = 10;

    private final AiProviderRegistry providerRegistry;
    private final AiToolRegistry toolRegistry;
    private final ProjectAccessGuard accessGuard;
    private final RateLimitService rateLimitService;
    private final SecretRedactionService redaction;
    private final DeployForgeProperties properties;

    public AiChatService(
            AiProviderRegistry providerRegistry,
            AiToolRegistry toolRegistry,
            ProjectAccessGuard accessGuard,
            RateLimitService rateLimitService,
            SecretRedactionService redaction,
            DeployForgeProperties properties) {
        this.providerRegistry = providerRegistry;
        this.toolRegistry = toolRegistry;
        this.accessGuard = accessGuard;
        this.rateLimitService = rateLimitService;
        this.redaction = redaction;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public ChatResponse chat(UUID projectId, UUID userId, ChatRequest request) {
        Project project = accessGuard.require(projectId, userId, Permission.USE_AI);
        rateLimitService.checkPerMinute(
                "ai-chat", userId.toString(), properties.rateLimit().aiChatPerMinute());

        AiToolRegistry.ToolContext toolContext =
                toolRegistry.gather(project, request.message(), request.deploymentId());

        AiChatContext context =
                new AiChatContext(
                        project.getName(),
                        toolContext.rendered(),
                        history(request.history()),
                        request.message().trim());

        AiProvider primary = providerRegistry.primary();
        AiChatResult result;
        try {
            result = primary.chat(context);
        } catch (AiProviderException e) {
            log.warn("ai_chat_primary_failed provider={} reason={}", primary.name(), e.getMessage());
            result = providerRegistry.fallback().chat(context);
        } catch (RuntimeException e) {
            log.warn("ai_chat_primary_error provider={}", primary.name(), e);
            result = providerRegistry.fallback().chat(context);
        }

        // The model can echo something it saw in a log line; redact on the way out too.
        String answer = redaction.redact(result.answer());

        return new ChatResponse(
                answer,
                result.provider(),
                result.model(),
                toolContext.usedTools(),
                !result.provider().equals(providerRegistry.fallback().name()),
                Instant.now());
    }

    public List<String> availableTools() {
        return toolRegistry.availableTools();
    }

    /**
     * Trims and normalises the client supplied history.
     *
     * <p>History arrives from the browser, so it is untrusted: roles are normalised, length is capped, and
     * content is redacted before it can be sent to a provider.
     */
    private List<ChatMessage> history(List<ChatTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return List.of();
        }
        List<ChatTurn> recent =
                turns.size() <= MAX_HISTORY_TURNS
                        ? turns
                        : turns.subList(turns.size() - MAX_HISTORY_TURNS, turns.size());
        return recent.stream()
                .map(
                        turn ->
                                new ChatMessage(
                                        "assistant".equalsIgnoreCase(turn.role()) ? "assistant" : "user",
                                        redaction.redact(turn.content())))
                .toList();
    }
}
