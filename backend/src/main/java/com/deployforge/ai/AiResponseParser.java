package com.deployforge.ai;

import com.deployforge.ai.AiModels.AiAnalysisResult;
import com.deployforge.ai.AiModels.Severity;
import com.deployforge.ai.AiModels.SuggestedFix;
import com.deployforge.common.util.JsonCodec;
import com.deployforge.security.SecretRedactionService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Turns raw model output into a validated {@link AiAnalysisResult}.
 *
 * <p>Model output is untrusted input. This parser therefore:
 *
 * <ul>
 *   <li>tolerates the common wrappers (markdown fences, leading prose) instead of failing on them
 *   <li>rejects anything without a root cause rather than showing an empty analysis card
 *   <li>clamps {@code confidence} to 0..1 and normalises unknown severities
 *   <li>caps field and list lengths so a runaway response cannot bloat the database or the UI
 *   <li>strips commands that look destructive - the platform must never present {@code rm -rf} as a
 *       suggested fix, no matter what the model said
 *   <li>redacts the output, because a model can echo a secret it saw in a log line back at us
 * </ul>
 */
@Component
public class AiResponseParser {

    private static final Logger log = LoggerFactory.getLogger(AiResponseParser.class);

    private static final int MAX_TEXT = 2000;
    private static final int MAX_EVIDENCE = 8;
    private static final int MAX_FIXES = 5;
    private static final int MAX_COMMAND = 400;

    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*}");

    /** Commands we refuse to surface even if the model suggests them. */
    private static final List<Pattern> DESTRUCTIVE_COMMANDS =
            List.of(
                    Pattern.compile("(?i)\\brm\\s+-[a-z]*f"),
                    Pattern.compile("(?i)\\bdocker\\s+system\\s+prune"),
                    Pattern.compile("(?i)\\bdocker\\s+(rm|rmi|kill)\\b"),
                    Pattern.compile("(?i)\\bdrop\\s+(database|table|schema)\\b"),
                    Pattern.compile("(?i)\\btruncate\\s+table\\b"),
                    Pattern.compile("(?i)\\bgit\\s+push\\s+.*--force"),
                    Pattern.compile("(?i)\\bkubectl\\s+delete\\b"),
                    Pattern.compile("(?i)\\bshutdown\\b|\\breboot\\b"),
                    Pattern.compile("(?i)\\bmkfs\\b|\\bdd\\s+if="),
                    Pattern.compile("(?i)\\bchmod\\s+777\\b"),
                    Pattern.compile("(?i)curl[^|]*\\|\\s*(ba)?sh"));

    private final JsonCodec json;
    private final SecretRedactionService redaction;

    public AiResponseParser(JsonCodec json, SecretRedactionService redaction) {
        this.json = json;
        this.redaction = redaction;
    }

    /**
     * @return the validated analysis, or empty when the response cannot be trusted
     */
    public Optional<AiAnalysisResult> parse(String rawResponse, String provider, String model) {
        if (!StringUtils.hasText(rawResponse)) {
            return Optional.empty();
        }
        JsonNode root = extractJson(rawResponse);
        if (root == null) {
            log.warn("ai_response_not_json provider={} length={}", provider, rawResponse.length());
            return Optional.empty();
        }

        String rootCause = text(root, "rootCause");
        String summary = text(root, "summary");
        if (!StringUtils.hasText(rootCause) && !StringUtils.hasText(summary)) {
            log.warn("ai_response_missing_fields provider={}", provider);
            return Optional.empty();
        }
        if (!StringUtils.hasText(rootCause)) {
            rootCause = summary;
        }
        if (!StringUtils.hasText(summary)) {
            summary = rootCause;
        }

        List<String> evidence = new ArrayList<>();
        JsonNode evidenceNode = root.get("evidence");
        if (evidenceNode != null && evidenceNode.isArray()) {
            for (JsonNode item : evidenceNode) {
                String value = truncate(redaction.redact(item.asText("")), 500);
                if (StringUtils.hasText(value) && evidence.size() < MAX_EVIDENCE) {
                    evidence.add(value);
                }
            }
        }

        List<SuggestedFix> fixes = new ArrayList<>();
        JsonNode fixesNode = root.get("suggestedFixes");
        if (fixesNode != null && fixesNode.isArray()) {
            for (JsonNode item : fixesNode) {
                if (fixes.size() >= MAX_FIXES) {
                    break;
                }
                String title = truncate(redaction.redact(text(item, "title")), 200);
                String description = truncate(redaction.redact(text(item, "description")), MAX_TEXT);
                String command = sanitizeCommand(text(item, "command"));
                if (StringUtils.hasText(title) || StringUtils.hasText(description)) {
                    fixes.add(
                            new SuggestedFix(
                                    StringUtils.hasText(title) ? title : "Suggested fix", description, command));
                }
            }
        }

        Severity severity = severity(text(root, "severity"));
        double confidence = confidence(root.get("confidence"));

        return Optional.of(
                new AiAnalysisResult(
                        truncate(redaction.redact(summary), MAX_TEXT),
                        truncate(redaction.redact(rootCause), MAX_TEXT),
                        evidence,
                        fixes,
                        severity,
                        confidence,
                        provider,
                        model,
                        truncate(redaction.redact(rawResponse), 20000)));
    }

    /** Finds the JSON object even when the model wrapped it in prose or a markdown fence. */
    private JsonNode extractJson(String rawResponse) {
        String candidate = rawResponse.trim();
        if (candidate.startsWith("```")) {
            int firstNewline = candidate.indexOf('\n');
            int lastFence = candidate.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                candidate = candidate.substring(firstNewline + 1, lastFence).trim();
            }
        }
        JsonNode parsed = tryParse(candidate);
        if (parsed != null) {
            return parsed;
        }
        Matcher matcher = JSON_OBJECT.matcher(rawResponse);
        if (matcher.find()) {
            return tryParse(matcher.group());
        }
        return null;
    }

    private JsonNode tryParse(String candidate) {
        try {
            JsonNode node = json.mapper().readTree(candidate);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText("");
        return text.isBlank() ? null : text.trim();
    }

    private Severity severity(String value) {
        if (value == null) {
            return Severity.MEDIUM;
        }
        try {
            return Severity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Severity.MEDIUM;
        }
    }

    private double confidence(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return 0.5;
        }
        double value = node.asDouble(0.5);
        // Some models answer 92 instead of 0.92.
        if (value > 1.0 && value <= 100.0) {
            value = value / 100.0;
        }
        return Math.max(0.0, Math.min(1.0, Math.round(value * 100d) / 100d));
    }

    private String sanitizeCommand(String command) {
        if (!StringUtils.hasText(command) || "null".equalsIgnoreCase(command.trim())) {
            return null;
        }
        String value = command.trim();
        if (value.length() > MAX_COMMAND) {
            return null;
        }
        for (Pattern pattern : DESTRUCTIVE_COMMANDS) {
            if (pattern.matcher(value).find()) {
                log.warn("ai_command_blocked pattern_matched");
                return null;
            }
        }
        return redaction.redact(value);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 3) + "...";
    }
}
