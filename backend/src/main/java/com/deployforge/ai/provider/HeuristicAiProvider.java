package com.deployforge.ai.provider;

import com.deployforge.ai.AiModels.AiAnalysisContext;
import com.deployforge.ai.AiModels.AiAnalysisResult;
import com.deployforge.ai.AiModels.AiChatContext;
import com.deployforge.ai.AiModels.AiChatResult;
import com.deployforge.ai.AiModels.Severity;
import com.deployforge.ai.AiModels.SuggestedFix;
import com.deployforge.ai.AiProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Local, rule based failure analyzer. No network, no model, no API key.
 *
 * <p>Two jobs:
 *
 * <ol>
 *   <li><b>Offline default.</b> A reviewer can clone the repository, deploy something broken and still see
 *       a real root cause without signing up for an AI vendor.
 *   <li><b>Fallback.</b> When the configured provider is down or over quota, the analysis card degrades to
 *       this instead of disappearing.
 * </ol>
 *
 * <p>It is labelled {@code heuristic} everywhere it is stored and displayed, and its confidence reflects
 * how specific the matched signal was. It reads the same real logs a model would - the rules below encode
 * failures that actually happen, they do not invent findings.
 */
@Component
public class HeuristicAiProvider implements AiProvider {

    /** An ordered rule: first match wins, so specific patterns are listed before general ones. */
    private record Rule(
            Pattern pattern,
            String rootCauseTemplate,
            String fixTitle,
            String fixDescription,
            String command,
            Severity severity,
            double confidence) {}

    private static final Pattern MISSING_ENV_PRISMA =
            Pattern.compile("Environment variable not found:\\s*([A-Z0-9_]+)");
    private static final Pattern MISSING_ENV_GENERIC =
            Pattern.compile(
                    "(?i)(?:missing|not set|not defined|undefined|required)[^\\n]{0,40}?\\b([A-Z][A-Z0-9_]{3,})\\b(?:[^\\n]{0,40}?(?:environment|env)\\b)?");
    private static final Pattern MODULE_NOT_FOUND =
            Pattern.compile("Cannot find module '([^']+)'");
    private static final Pattern PY_MODULE_NOT_FOUND =
            Pattern.compile("ModuleNotFoundError: No module named '([^']+)'");
    private static final Pattern PORT_IN_USE = Pattern.compile("(?i)EADDRINUSE|address already in use");

    private static final List<Rule> RULES =
            List.of(
                    new Rule(
                            Pattern.compile("(?i)npm ERR!.*(ERESOLVE|peer dep)"),
                            "npm could not resolve the dependency tree: conflicting peer dependencies.",
                            "Resolve the peer dependency conflict",
                            "Align the conflicting package versions in package.json, or commit a lockfile that already "
                                    + "resolves them. Installing with --legacy-peer-deps hides the conflict rather than fixing it.",
                            "npm install --legacy-peer-deps",
                            Severity.HIGH,
                            0.82),
                    new Rule(
                            Pattern.compile("(?i)npm ERR!.*(ENOENT|missing script)"),
                            "The npm script the build tried to run does not exist in package.json.",
                            "Add the missing npm script",
                            "DeployForge runs the build and start commands shown in the deployment configuration. "
                                    + "Either add the script to package.json or change the command in project settings.",
                            null,
                            Severity.HIGH,
                            0.85),
                    new Rule(
                            Pattern.compile("(?i)npm ci.*(can only install|lock file|package-lock\\.json)"),
                            "`npm ci` requires a lockfile that matches package.json, and this repository does not have one.",
                            "Commit a lockfile, or install with npm install",
                            "Run npm install locally and commit the generated package-lock.json. That also makes builds "
                                    + "reproducible. Alternatively change the install command to `npm install`.",
                            "npm install",
                            Severity.HIGH,
                            0.9),
                    new Rule(
                            Pattern.compile("(?i)(TS\\d{4}:|error TS\\d{4})"),
                            "TypeScript compilation failed.",
                            "Fix the TypeScript errors",
                            "The compiler errors are quoted in the evidence. Reproduce them locally with the same build "
                                    + "command to iterate quickly.",
                            "npm run build",
                            Severity.HIGH,
                            0.85),
                    new Rule(
                            Pattern.compile("(?i)(COMPILATION ERROR|BUILD FAILURE).*"),
                            "The JVM build failed to compile the project.",
                            "Fix the compilation errors",
                            "Run the same Maven or Gradle command locally; the failing sources are listed in the build "
                                    + "output above.",
                            "mvn -B -DskipTests package",
                            Severity.HIGH,
                            0.8),
                    new Rule(
                            Pattern.compile("(?i)(OOMKilled|out of memory|Killed process|java\\.lang\\.OutOfMemoryError)"),
                            "The container was killed for exceeding its memory limit.",
                            "Raise the memory limit or reduce memory use",
                            "The container runs with a hard memory cap. Increase DEPLOYMENT_MEMORY_LIMIT_MB on the "
                                    + "platform, or lower the application's heap and worker counts.",
                            null,
                            Severity.CRITICAL,
                            0.88),
                    new Rule(
                            Pattern.compile("(?i)(ECONNREFUSED|could not connect to server|connection refused).*(5432|postgres|mysql|3306|redis|6379)"),
                            "The application could not reach its database or cache.",
                            "Point the connection URL at a reachable host",
                            "A deployed container cannot reach services on your laptop's localhost. Use a hostname the "
                                    + "container can resolve and confirm the credentials are set as environment variables.",
                            null,
                            Severity.HIGH,
                            0.84),
                    new Rule(
                            Pattern.compile("(?i)permission denied|EACCES"),
                            "The process was denied filesystem or port access inside the container.",
                            "Avoid privileged paths and ports",
                            "Containers run unprivileged. Write to a path your user owns and listen on a port above 1024, "
                                    + "or bind the port DeployForge injects as PORT.",
                            null,
                            Severity.MEDIUM,
                            0.7));

    @Override
    public String name() {
        return "heuristic";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public AiAnalysisResult analyzeDeploymentFailure(AiAnalysisContext context) {
        String haystack = String.join("\n", allLines(context));

        // Order is from most specific to least: a named variable or module beats a generic rule, and a
        // generic rule beats "the health check timed out", which is a symptom rather than a cause.
        Optional<AiAnalysisResult> specific = analyzeSpecific(context, haystack);
        if (specific.isPresent()) {
            return specific.get();
        }
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(haystack);
            if (matcher.find()) {
                return build(
                        context,
                        rule.rootCauseTemplate(),
                        rule.rootCauseTemplate(),
                        evidenceAround(context, matcher.group()),
                        List.of(new SuggestedFix(rule.fixTitle(), rule.fixDescription(), rule.command())),
                        rule.severity(),
                        rule.confidence());
            }
        }
        if ("HEALTH_CHECKING".equals(context.failureStage())) {
            return healthCheckDiagnosis(context);
        }
        return fallback(context);
    }

    /** Patterns that can name the exact variable, module or port involved. */
    private Optional<AiAnalysisResult> analyzeSpecific(AiAnalysisContext context, String haystack) {
        Matcher prisma = MISSING_ENV_PRISMA.matcher(haystack);
        if (prisma.find()) {
            return Optional.of(missingVariable(context, prisma.group(1), haystack, 0.96));
        }

        Matcher node = MODULE_NOT_FOUND.matcher(haystack);
        if (node.find()) {
            String module = node.group(1);
            boolean relative = module.startsWith(".") || module.startsWith("/");
            return Optional.of(
                    build(
                            context,
                            relative
                                    ? "The application imports '" + module + "', which does not exist in the built image."
                                    : "The dependency '" + module + "' is not installed in the image.",
                            relative
                                    ? "A relative import points at a file that was not committed or was excluded from the build."
                                    : "'"
                                            + module
                                            + "' is imported at runtime but is missing from the installed dependencies.",
                            evidenceAround(context, node.group()),
                            List.of(
                                    relative
                                            ? new SuggestedFix(
                                                    "Commit the missing file",
                                                    "Check the import path and confirm the file is committed and not ignored by "
                                                            + ".gitignore or .dockerignore.",
                                                    null)
                                            : new SuggestedFix(
                                                    "Add " + module + " to dependencies",
                                                    "It is likely a devDependency or was installed locally only. Move it to "
                                                            + "dependencies so the production install includes it.",
                                                    "npm install " + module + " --save")),
                            Severity.HIGH,
                            0.93));
        }

        Matcher python = PY_MODULE_NOT_FOUND.matcher(haystack);
        if (python.find()) {
            String module = python.group(1);
            return Optional.of(
                    build(
                            context,
                            "The Python package '" + module + "' is not installed in the image.",
                            "'" + module + "' is imported at runtime but is not listed in requirements.txt.",
                            evidenceAround(context, python.group()),
                            List.of(
                                    new SuggestedFix(
                                            "Add " + module + " to requirements.txt",
                                            "Pin the version you tested with, then redeploy.",
                                            "pip freeze | grep -i " + module)),
                            Severity.HIGH,
                            0.93));
        }

        if (PORT_IN_USE.matcher(haystack).find()) {
            return Optional.of(
                    build(
                            context,
                            "The application tried to bind a port that was already in use inside the container.",
                            "The process binds a hard coded port instead of the PORT variable DeployForge injects.",
                            evidenceAround(context, "EADDRINUSE"),
                            List.of(
                                    new SuggestedFix(
                                            "Listen on the injected PORT",
                                            "DeployForge sets PORT="
                                                    + context.containerPort()
                                                    + " in the container. Read it instead of hard coding a port.",
                                            null)),
                            Severity.HIGH,
                            0.8));
        }

        Matcher env = MISSING_ENV_GENERIC.matcher(haystack);
        if (env.find()) {
            String candidate = env.group(1);
            if (!candidate.equals("PORT") && candidate.length() >= 4) {
                return Optional.of(missingVariable(context, candidate, haystack, 0.78));
            }
        }
        return Optional.empty();
    }

    /**
     * Diagnosis for "the container came up but never answered".
     *
     * <p>Reached only when no more specific signal matched. A non-zero exit code changes the conclusion
     * entirely - the process died rather than failed to serve - so it is handled separately.
     */
    private AiAnalysisResult healthCheckDiagnosis(AiAnalysisContext context) {
        boolean exited = context.exitCode() != null && context.exitCode() != 0;
        String rootCause =
                exited
                        ? "The process exited with code "
                                + context.exitCode()
                                + " shortly after start, so it never served a request."
                        : "The process is running but is not serving HTTP on port "
                                + context.containerPort()
                                + ", or it binds 127.0.0.1 instead of 0.0.0.0.";

        List<SuggestedFix> fixes = new ArrayList<>();
        if (exited) {
            fixes.add(
                    new SuggestedFix(
                            "Check the start command",
                            "'"
                                    + context.startCommand()
                                    + "' exited immediately. Run it locally against a production build to see the error it "
                                    + "printed.",
                            context.startCommand()));
        } else {
            fixes.add(
                    new SuggestedFix(
                            "Bind 0.0.0.0 and the injected PORT",
                            "A server bound to 127.0.0.1 is unreachable from outside the container. Listen on 0.0.0.0 "
                                    + "and read the PORT variable DeployForge sets.",
                            null));
        }
        fixes.add(
                new SuggestedFix(
                        "Check the health check path",
                        "DeployForge treats any 2xx or 3xx response as healthy. Point the health check path at a route "
                                + "that exists ("
                                + (context.healthCheckPath() == null ? "/" : context.healthCheckPath())
                                + " is being probed).",
                        null));

        return build(
                context,
                "The container started but never answered the health check on "
                        + (context.healthCheckPath() == null ? "/" : context.healthCheckPath())
                        + ".",
                rootCause,
                evidenceFromLogs(context),
                fixes,
                Severity.HIGH,
                0.72);
    }

    private AiAnalysisResult missingVariable(
            AiAnalysisContext context, String variable, String haystack, double confidence) {
        boolean configured = context.environmentVariableNames().contains(variable);
        String rootCause =
                configured
                        ? "The application read "
                                + variable
                                + " but rejected its value, so the variable is set yet not usable."
                        : "The required environment variable "
                                + variable
                                + " is not configured for this environment.";
        List<SuggestedFix> fixes = new ArrayList<>();
        if (configured) {
            fixes.add(
                    new SuggestedFix(
                            "Check the value of " + variable,
                            variable
                                    + " exists in this environment, so the failure is about its content - a wrong host, a "
                                    + "missing scheme, or an unreachable service. Rotate it in Project -> Environment.",
                            null));
        } else {
            fixes.add(
                    new SuggestedFix(
                            "Add " + variable + " to the environment",
                            "Open Project -> Environment, add "
                                    + variable
                                    + ", then redeploy. Values are encrypted before storage and never shown again.",
                            null));
        }
        return build(
                context,
                variable + " is missing from the environment.",
                rootCause,
                evidenceAround(context, variable),
                fixes,
                Severity.HIGH,
                confidence);
    }

    private AiAnalysisResult fallback(AiAnalysisContext context) {
        return build(
                context,
                "The deployment failed during "
                        + (context.failureStage() == null ? "the pipeline" : context.failureStage().toLowerCase(Locale.ROOT))
                        + ".",
                context.failureMessage() == null
                        ? "No recognised failure signature was found in the captured output."
                        : context.failureMessage(),
                evidenceFromLogs(context),
                List.of(
                        new SuggestedFix(
                                "Reproduce the failing command locally",
                                "Run the install and build commands from the deployment configuration on a clean checkout. "
                                        + "A failure that reproduces locally is much faster to fix.",
                                context.buildCommand()),
                        new SuggestedFix(
                                "Configure an AI provider for a deeper explanation",
                                "Set AI_PROVIDER and the matching API key to have the full log analysed by a model instead "
                                        + "of the built-in rules.",
                                null)),
                Severity.MEDIUM,
                0.35);
    }

    private AiAnalysisResult build(
            AiAnalysisContext context,
            String summary,
            String rootCause,
            List<String> evidence,
            List<SuggestedFix> fixes,
            Severity severity,
            double confidence) {
        return new AiAnalysisResult(
                summary,
                rootCause,
                evidence,
                fixes,
                severity,
                confidence,
                name(),
                "rule-based",
                null);
    }

    // ------------------------------------------------------------------ chat

    /**
     * Answers from the supplied context without a model.
     *
     * <p>Intentionally modest: it surfaces the relevant part of the gathered context and says plainly that
     * a provider is needed for real reasoning. Pretending to be a model would be worse than being useful
     * and honest.
     */
    @Override
    public AiChatResult chat(AiChatContext context) {
        String answer =
                """
                No AI provider is configured, so this answer comes from DeployForge's local rules rather than a model.

                Here is the current context for %s:

                %s

                Set AI_PROVIDER=gemini or openai (with the matching API key) to ask free-form questions about
                these deployments.
                """
                        .formatted(context.projectName(), context.projectContext());
        return new AiChatResult(answer.trim(), name(), "rule-based", List.of());
    }

    // ------------------------------------------------------------------ helpers

    private List<String> allLines(AiAnalysisContext context) {
        List<String> lines = new ArrayList<>();
        if (context.failureMessage() != null) {
            lines.add(context.failureMessage());
        }
        lines.addAll(context.errorLogs());
        lines.addAll(context.recentLogs());
        return lines;
    }

    /** Real log lines mentioning the matched token, so the card quotes evidence rather than paraphrasing. */
    private List<String> evidenceAround(AiAnalysisContext context, String token) {
        String needle = token == null ? "" : token.toLowerCase(Locale.ROOT);
        List<String> matches =
                allLines(context).stream()
                        .filter(line -> line.toLowerCase(Locale.ROOT).contains(needle))
                        .distinct()
                        .limit(4)
                        .toList();
        return matches.isEmpty() ? evidenceFromLogs(context) : matches;
    }

    private List<String> evidenceFromLogs(AiAnalysisContext context) {
        if (!context.errorLogs().isEmpty()) {
            return context.errorLogs().stream().limit(4).toList();
        }
        List<String> recent = context.recentLogs();
        if (recent.isEmpty()) {
            return List.of("No log output was captured for this deployment.");
        }
        return recent.subList(Math.max(0, recent.size() - 4), recent.size());
    }
}
