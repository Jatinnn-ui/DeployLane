package com.deployforge.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.deployforge.ai.AiModels.AiAnalysisContext;
import com.deployforge.ai.AiModels.AiAnalysisResult;
import com.deployforge.ai.AiModels.Severity;
import com.deployforge.ai.provider.HeuristicAiProvider;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The local analyzer is what a reviewer sees without an API key, and the fallback when a provider is down.
 * Every assertion here uses log output shaped like the real thing.
 */
class HeuristicAiProviderTest {

    private final HeuristicAiProvider provider = new HeuristicAiProvider();

    @Test
    @DisplayName("names the exact variable Prisma complained about")
    void detectsMissingPrismaVariable() {
        AiAnalysisResult result =
                provider.analyzeDeploymentFailure(
                        context(
                                "HEALTH_CHECKING",
                                List.of("JWT_SECRET"),
                                List.of(
                                        "APPLICATION ERROR PrismaClientInitializationError: error: Environment variable not found: DATABASE_URL.",
                                        "APPLICATION ERROR     at new PrismaClient")));

        assertThat(result.rootCause()).contains("DATABASE_URL");
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
        assertThat(result.confidence()).isGreaterThan(0.9);
        assertThat(result.suggestedFixes()).isNotEmpty();
        assertThat(result.suggestedFixes().get(0).title()).contains("DATABASE_URL");
        assertThat(result.evidence()).anyMatch(line -> line.contains("DATABASE_URL"));
        assertThat(result.provider()).isEqualTo("heuristic");
    }

    @Test
    @DisplayName("distinguishes a variable that exists but holds a bad value")
    void detectsConfiguredButInvalidVariable() {
        AiAnalysisResult result =
                provider.analyzeDeploymentFailure(
                        context(
                                "HEALTH_CHECKING",
                                List.of("DATABASE_URL"),
                                List.of("Environment variable not found: DATABASE_URL")));

        assertThat(result.rootCause()).contains("rejected its value");
    }

    @Test
    @DisplayName("identifies a missing Node dependency and suggests installing it")
    void detectsMissingNodeModule() {
        AiAnalysisResult result =
                provider.analyzeDeploymentFailure(
                        context(
                                "IMAGE_BUILDING",
                                List.of(),
                                List.of("BUILD ERROR Error: Cannot find module 'date-fns'")));

        assertThat(result.rootCause()).contains("date-fns");
        assertThat(result.suggestedFixes().get(0).command()).contains("npm install date-fns");
    }

    @Test
    @DisplayName("treats a relative import differently from a package")
    void detectsMissingRelativeImport() {
        AiAnalysisResult result =
                provider.analyzeDeploymentFailure(
                        context(
                                "IMAGE_BUILDING",
                                List.of(),
                                List.of("Error: Cannot find module './lib/helpers'")));

        assertThat(result.rootCause()).contains("relative import");
        assertThat(result.suggestedFixes().get(0).command()).isNull();
    }

    @Test
    @DisplayName("identifies a missing Python package")
    void detectsMissingPythonModule() {
        AiAnalysisResult result =
                provider.analyzeDeploymentFailure(
                        context(
                                "HEALTH_CHECKING",
                                List.of(),
                                List.of("ModuleNotFoundError: No module named 'httpx'")));

        assertThat(result.rootCause()).contains("httpx");
        assertThat(result.suggestedFixes().get(0).title()).contains("requirements.txt");
    }

    @Test
    @DisplayName("explains npm ci failing without a lockfile")
    void detectsNpmCiWithoutLockfile() {
        AiAnalysisResult result =
                provider.analyzeDeploymentFailure(
                        context(
                                "IMAGE_BUILDING",
                                List.of(),
                                List.of(
                                        "npm error `npm ci` can only install packages when your package.json and package-lock.json are in sync")));

        assertThat(result.rootCause()).contains("lockfile");
        assertThat(result.suggestedFixes().get(0).command()).isEqualTo("npm install");
    }

    @Test
    @DisplayName("recognises an out of memory kill as critical")
    void detectsOutOfMemory() {
        AiAnalysisResult result =
                provider.analyzeDeploymentFailure(
                        context("HEALTH_CHECKING", List.of(), List.of("java.lang.OutOfMemoryError: Java heap space")));

        assertThat(result.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(result.rootCause()).contains("memory");
    }

    @Test
    @DisplayName("a health check timeout with no signal still produces a specific diagnosis")
    void explainsHealthCheckTimeout() {
        AiAnalysisResult result =
                provider.analyzeDeploymentFailure(
                        context("HEALTH_CHECKING", List.of(), List.of("Server started on 127.0.0.1:3000")));

        assertThat(result.rootCause()).contains("0.0.0.0");
        assertThat(result.suggestedFixes()).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("a non-zero exit code changes the conclusion from 'not serving' to 'died on start'")
    void explainsCrashOnStartup() {
        AiAnalysisResult result =
                provider.analyzeDeploymentFailure(
                        context("HEALTH_CHECKING", List.of(), List.of("starting worker"), 137));

        assertThat(result.rootCause()).contains("exited with code 137");
        assertThat(result.suggestedFixes().get(0).title()).contains("start command");
    }

    @Test
    @DisplayName("with no recognisable signal it lowers confidence instead of inventing a cause")
    void staysHonestWhenUnsure() {
        AiAnalysisResult result =
                provider.analyzeDeploymentFailure(
                        context("IMAGE_BUILDING", List.of(), List.of("some unremarkable output")));

        assertThat(result.confidence()).isLessThan(0.5);
        assertThat(result.rootCause()).isNotBlank();
    }

    @Test
    @DisplayName("is always considered configured, since it needs nothing")
    void alwaysConfigured() {
        assertThat(provider.isConfigured()).isTrue();
        assertThat(provider.name()).isEqualTo("heuristic");
    }

    private AiAnalysisContext context(String stage, List<String> variableNames, List<String> logs) {
        return context(stage, variableNames, logs, null);
    }

    private AiAnalysisContext context(
            String stage, List<String> variableNames, List<String> logs, Integer exitCode) {
        return new AiAnalysisContext(
                "Maya AI",
                "Next.js",
                "Node.js",
                stage,
                "Health check never succeeded within 90s",
                exitCode,
                "npm ci",
                "npm run build",
                "npm start",
                3000,
                "/",
                "main",
                "f39a821",
                null,
                false,
                variableNames,
                logs,
                logs.stream().filter(line -> line.toLowerCase().contains("error")).toList());
    }
}
