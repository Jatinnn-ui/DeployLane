package com.deployforge.ai;

import com.deployforge.ai.AiModels.AiAnalysisContext;
import com.deployforge.ai.AiModels.AiChatContext;
import com.deployforge.ai.AiModels.ChatMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Builds the prompts sent to an AI provider.
 *
 * <p>Kept in one place so both providers send the same instructions, and so the prompt is reviewable as
 * code rather than buried in a client. The rules encoded here are the ones that make the output usable:
 * answer only from supplied evidence, separate fact from hypothesis, return strict JSON, never ask for
 * secrets, and state uncertainty instead of inventing a cause.
 */
@Component
public class AiPromptFactory {

    private static final String ANALYSIS_SYSTEM_PROMPT =
            """
            You are an experienced DevOps engineer reviewing a failed application deployment.

            Rules you must follow:
            - Analyse the failure using ONLY the supplied technical evidence.
            - Identify the single most probable root cause.
            - Do not invent files, commands, environment variables, dependencies or infrastructure that
              are not present in the evidence.
            - Clearly separate facts (quoted from the logs) from hypotheses.
            - Never request, guess or output secret values. You receive environment variable NAMES only,
              which is deliberate.
            - Prefer concrete, actionable remediation over general advice.
            - If the evidence is insufficient, say so and lower your confidence rather than speculating.
            - Reply with a single valid JSON object and nothing else: no markdown fence, no commentary.

            Required JSON schema:
            {
              "summary": "one or two sentences a developer can read in five seconds",
              "rootCause": "the most probable cause, specific and technical",
              "evidence": ["short quoted facts from the supplied logs"],
              "suggestedFixes": [
                {"title": "short imperative title",
                 "description": "what to do and why it fixes the root cause",
                 "command": "a single safe command, or null"}
              ],
              "severity": "LOW | MEDIUM | HIGH | CRITICAL",
              "confidence": 0.0
            }

            Commands must be read-only or clearly safe to run locally. Never suggest destructive
            operations, credential rotation performed on the user's behalf, or anything that deletes
            infrastructure.
            """;

    private static final String CHAT_SYSTEM_PROMPT =
            """
            You are DeployForge's DevOps assistant, embedded in a deployment platform.

            You are given a pre-gathered, redacted context block about one project: its latest
            deployments, statuses, recent log excerpts, health and container resource usage. You cannot
            query anything else and you cannot run commands.

            Rules:
            - Answer only from the supplied context. If the answer is not in it, say what is missing and
              which page of DeployForge would show it.
            - Be concise and technical. Developers are reading this between deploys.
            - You may recommend commands, but never claim to have executed one.
            - Never output secret values. The context contains variable names only.
            - Do not recommend destructive actions (deleting deployments, dropping databases, rotating
              credentials) without stating the risk and that the user must perform it deliberately.
            - Plain text or short markdown. No preamble such as "Certainly".
            """;

    public String analysisSystemPrompt() {
        return ANALYSIS_SYSTEM_PROMPT;
    }

    public String chatSystemPrompt() {
        return CHAT_SYSTEM_PROMPT;
    }

    /** Renders the failure evidence as a compact, stable block. */
    public String analysisUserPrompt(AiAnalysisContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Deployment failure report\n");
        prompt.append("=========================\n");
        append(prompt, "Project", context.projectName());
        append(prompt, "Framework", context.framework());
        append(prompt, "Runtime", context.runtime());
        append(prompt, "Failed stage", context.failureStage());
        append(prompt, "Platform message", context.failureMessage());
        append(prompt, "Exit code", context.exitCode() == null ? null : String.valueOf(context.exitCode()));
        append(prompt, "Branch", context.branch());
        append(prompt, "Commit", context.commitSha());
        append(prompt, "Install command", context.installCommand());
        append(prompt, "Build command", context.buildCommand());
        append(prompt, "Start command", context.startCommand());
        append(
                prompt,
                "Container port",
                context.containerPort() == null ? null : String.valueOf(context.containerPort()));
        append(prompt, "Health check path", context.healthCheckPath());
        append(
                prompt,
                "Dockerfile",
                context.dockerfileSource() == null
                        ? null
                        : (context.dockerfileGenerated() ? "generated by DeployForge" : "committed by the user"));

        prompt.append("\nAvailable environment variable names (values intentionally withheld):\n");
        if (context.environmentVariableNames().isEmpty()) {
            prompt.append("  (none configured)\n");
        } else {
            context.environmentVariableNames().forEach(name -> prompt.append("  ").append(name).append('\n'));
        }

        if (StringUtils.hasText(context.dockerfileSource())) {
            prompt.append("\nDockerfile used for this build:\n");
            prompt.append("```\n").append(context.dockerfileSource()).append("\n```\n");
        }

        if (!context.errorLogs().isEmpty()) {
            prompt.append("\nError log lines:\n");
            context.errorLogs().forEach(line -> prompt.append("  ").append(line).append('\n'));
        }

        prompt.append("\nRecent log output (oldest first):\n");
        if (context.recentLogs().isEmpty()) {
            prompt.append("  (no log output was captured)\n");
        } else {
            context.recentLogs().forEach(line -> prompt.append("  ").append(line).append('\n'));
        }

        prompt.append("\nReturn the JSON object described in your instructions.\n");
        return prompt.toString();
    }

    public String chatUserPrompt(AiChatContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Project context\n");
        prompt.append("===============\n");
        prompt.append(context.projectContext()).append('\n');

        List<ChatMessage> history = context.history();
        if (history != null && !history.isEmpty()) {
            prompt.append("\nConversation so far:\n");
            for (ChatMessage message : history) {
                prompt.append(message.role().equals("user") ? "User: " : "Assistant: ")
                        .append(message.content())
                        .append('\n');
            }
        }

        prompt.append("\nQuestion: ").append(context.question()).append('\n');
        return prompt.toString();
    }

    private void append(StringBuilder prompt, String label, String value) {
        if (StringUtils.hasText(value)) {
            prompt.append(label).append(": ").append(value).append('\n');
        }
    }
}
