package com.deployforge.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.deployforge.ai.AiModels.AiAnalysisResult;
import com.deployforge.ai.AiModels.Severity;
import com.deployforge.common.util.JsonCodec;
import com.deployforge.security.SecretRedactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Model output is untrusted input. These tests cover the ways a model realistically misbehaves: fenced
 * JSON, prose around the object, percentages instead of fractions, unknown severities, missing fields and
 * dangerous command suggestions.
 */
class AiResponseParserTest {

    private final AiResponseParser parser =
            new AiResponseParser(new JsonCodec(new ObjectMapper()), new SecretRedactionService());

    @Test
    @DisplayName("parses a well formed response")
    void parsesValidResponse() {
        String response =
                """
                {
                  "summary": "DATABASE_URL is missing.",
                  "rootCause": "The application requires DATABASE_URL but it is not configured.",
                  "evidence": ["PrismaClientInitializationError: Environment variable not found: DATABASE_URL"],
                  "suggestedFixes": [
                    {"title": "Add DATABASE_URL", "description": "Set it in project settings.", "command": "npx prisma generate"}
                  ],
                  "severity": "HIGH",
                  "confidence": 0.96
                }
                """;

        AiAnalysisResult result = parser.parse(response, "gemini", "gemini-2.0-flash").orElseThrow();

        assertThat(result.rootCause()).contains("DATABASE_URL");
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.suggestedFixes()).hasSize(1);
        assertThat(result.suggestedFixes().get(0).command()).isEqualTo("npx prisma generate");
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
        assertThat(result.confidence()).isEqualTo(0.96);
        assertThat(result.provider()).isEqualTo("gemini");
    }

    @Test
    @DisplayName("unwraps a markdown fenced object")
    void parsesFencedResponse() {
        String response =
                """
                ```json
                {"summary":"Build failed.","rootCause":"Missing module 'lodash'.","evidence":[],"suggestedFixes":[],"severity":"MEDIUM","confidence":0.7}
                ```
                """;

        assertThat(parser.parse(response, "openai", "gpt-4o-mini")).isPresent();
    }

    @Test
    @DisplayName("finds the object even when the model adds prose around it")
    void parsesResponseWithSurroundingText() {
        String response =
                "Here is my analysis:\n{\"rootCause\":\"Port mismatch.\",\"summary\":\"Wrong port.\",\"confidence\":0.5}\nHope that helps!";

        assertThat(parser.parse(response, "openai", "gpt-4o-mini")).isPresent();
    }

    @Test
    @DisplayName("a percentage confidence is normalised to a fraction")
    void normalisesPercentageConfidence() {
        String response = "{\"rootCause\":\"Missing env var.\",\"confidence\":92}";

        AiAnalysisResult result = parser.parse(response, "gemini", "m").orElseThrow();

        assertThat(result.confidence()).isEqualTo(0.92);
    }

    @Test
    @DisplayName("out of range confidence is clamped")
    void clampsConfidence() {
        assertThat(parser.parse("{\"rootCause\":\"x\",\"confidence\":-4}", "p", "m").orElseThrow().confidence())
                .isEqualTo(0.0);
        assertThat(parser.parse("{\"rootCause\":\"x\",\"confidence\":500}", "p", "m").orElseThrow().confidence())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("an unknown severity falls back to MEDIUM instead of failing")
    void normalisesSeverity() {
        AiAnalysisResult result =
                parser.parse("{\"rootCause\":\"x\",\"severity\":\"CATASTROPHIC\"}", "p", "m").orElseThrow();

        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    @DisplayName("non-JSON and empty output is rejected rather than displayed")
    void rejectsUnusableOutput() {
        assertThat(parser.parse("I could not analyse this deployment.", "p", "m")).isEmpty();
        assertThat(parser.parse("", "p", "m")).isEmpty();
        assertThat(parser.parse(null, "p", "m")).isEmpty();
        assertThat(parser.parse("{\"evidence\":[]}", "p", "m")).isEmpty();
    }

    @Test
    @DisplayName("a missing summary is filled from the root cause")
    void backfillsSummary() {
        AiAnalysisResult result =
                parser.parse("{\"rootCause\":\"The start command exited immediately.\"}", "p", "m")
                        .orElseThrow();

        assertThat(result.summary()).isEqualTo("The start command exited immediately.");
    }

    @Test
    @DisplayName("destructive commands are stripped from suggested fixes")
    void blocksDestructiveCommands() {
        String response =
                """
                {"rootCause":"Disk full.","suggestedFixes":[
                  {"title":"Clean up","description":"Free space.","command":"rm -rf /var/lib/deployforge"},
                  {"title":"Prune","description":"Remove images.","command":"docker system prune -a"},
                  {"title":"Drop","description":"Reset schema.","command":"DROP TABLE deployments"},
                  {"title":"Inspect","description":"Check usage.","command":"df -h"}
                ]}
                """;

        AiAnalysisResult result = parser.parse(response, "p", "m").orElseThrow();

        assertThat(result.suggestedFixes()).hasSize(4);
        assertThat(result.suggestedFixes().get(0).command()).isNull();
        assertThat(result.suggestedFixes().get(1).command()).isNull();
        assertThat(result.suggestedFixes().get(2).command()).isNull();
        assertThat(result.suggestedFixes().get(3).command()).isEqualTo("df -h");
    }

    @Test
    @DisplayName("a secret echoed back by the model is redacted")
    void redactsEchoedSecrets() {
        String response =
                "{\"rootCause\":\"Connection string postgres://admin:hunter2000@db:5432/app is wrong.\"}";

        AiAnalysisResult result = parser.parse(response, "p", "m").orElseThrow();

        assertThat(result.rootCause()).doesNotContain("hunter2000");
    }

    @Test
    @DisplayName("evidence and fixes are capped so a runaway response cannot bloat the UI")
    void capsListSizes() {
        StringBuilder evidence = new StringBuilder();
        StringBuilder fixes = new StringBuilder();
        for (int index = 0; index < 30; index++) {
            evidence.append("\"line ").append(index).append("\",");
            fixes.append("{\"title\":\"fix ").append(index).append("\"},");
        }
        String response =
                "{\"rootCause\":\"x\",\"evidence\":["
                        + evidence.substring(0, evidence.length() - 1)
                        + "],\"suggestedFixes\":["
                        + fixes.substring(0, fixes.length() - 1)
                        + "]}";

        Optional<AiAnalysisResult> result = parser.parse(response, "p", "m");

        assertThat(result).isPresent();
        assertThat(result.get().evidence()).hasSizeLessThanOrEqualTo(8);
        assertThat(result.get().suggestedFixes()).hasSizeLessThanOrEqualTo(5);
    }
}
