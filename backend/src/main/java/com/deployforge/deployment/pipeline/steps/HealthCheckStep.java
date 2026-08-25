package com.deployforge.deployment.pipeline.steps;

import com.deployforge.config.DeployForgeProperties;
import com.deployforge.config.HttpClientConfig;
import com.deployforge.deployment.DeploymentStepName;
import com.deployforge.deployment.pipeline.DeploymentContext;
import com.deployforge.deployment.pipeline.PipelineStep;
import com.deployforge.deployment.pipeline.StepCancelledException;
import com.deployforge.deployment.pipeline.StepFailedException;
import com.deployforge.log.DeploymentLogService;
import com.deployforge.log.LogLevel;
import com.deployforge.log.LogSource;
import com.deployforge.runtime.DeploymentRuntime;
import com.deployforge.runtime.RuntimeModels.RuntimeStats;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Probes the freshly started container until it answers, or gives up.
 *
 * <p>Two things make this more than a sleep-and-hope:
 *
 * <ul>
 *   <li><b>Crash detection.</b> Between probes the container's state is inspected. A container that has
 *       already exited will never become healthy, so the step fails immediately with the exit code
 *       instead of burning the full timeout - and pulls the container's own output into the deployment
 *       log, which is exactly the evidence the AI analyzer needs.
 *   <li><b>Reachability fallback.</b> DeployForge may run on the host (published port reachable at
 *       {@code localhost}) or inside Docker Compose (reachable at the container name on the shared
 *       network). Both candidates are tried rather than assuming a topology.
 * </ul>
 */
@Component
public class HealthCheckStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckStep.class);
    private static final int LOG_EVERY_N_ATTEMPTS = 5;
    private static final int CONTAINER_LOG_LINES = 120;

    private final RestClient healthClient;
    private final DeploymentRuntime runtime;
    private final DeploymentLogService logService;
    private final DeployForgeProperties properties;

    public HealthCheckStep(
            @Qualifier(HttpClientConfig.HEALTH_CHECK_CLIENT) RestClient healthClient,
            DeploymentRuntime runtime,
            DeploymentLogService logService,
            DeployForgeProperties properties) {
        this.healthClient = healthClient;
        this.runtime = runtime;
        this.logService = logService;
        this.properties = properties;
    }

    @Override
    public DeploymentStepName name() {
        return DeploymentStepName.HEALTH_CHECK;
    }

    @Override
    public void execute(DeploymentContext context) {
        String path =
                context.getHealthCheckPath() == null || context.getHealthCheckPath().isBlank()
                        ? properties.deployment().healthCheckPath()
                        : context.getHealthCheckPath();

        List<String> candidates = candidateUrls(context, path);
        Duration timeout = properties.deployment().healthCheckTimeout();
        Duration interval = properties.deployment().healthCheckInterval();
        Instant deadline = Instant.now().plus(timeout);

        logService.append(
                context.getDeploymentId(),
                LogLevel.INFO,
                LogSource.HEALTH,
                "Probing " + candidates.get(0) + " every " + interval.toSeconds() + "s for up to " + timeout.toSeconds() + "s");

        int attempt = 0;
        String lastError = "no response yet";

        while (Instant.now().isBefore(deadline)) {
            if (context.isCancelled()) {
                throw new StepCancelledException("Health check cancelled");
            }
            attempt++;

            ContainerState state = inspectContainer(context);
            if (state.exited()) {
                collectContainerLogs(context);
                throw new StepFailedException(
                        "The container exited before it became healthy"
                                + (state.exitCode() == null ? "" : " (exit code " + state.exitCode() + ")"),
                        "Read the application output above. A non-zero exit on startup is usually a missing "
                                + "environment variable, a failed database connection or a wrong start command.",
                        state.exitCode());
            }

            for (String url : candidates) {
                Optional<Integer> status = probe(url);
                if (status.isPresent()) {
                    int code = status.get();
                    if (code >= 200 && code < 400) {
                        logService.append(
                                context.getDeploymentId(),
                                LogLevel.INFO,
                                LogSource.HEALTH,
                                "Health check passed: " + url + " responded " + code + " after " + attempt + " attempt(s)");
                        return;
                    }
                    lastError = url + " responded " + code;
                } else {
                    lastError = url + " is not accepting connections yet";
                }
            }

            if (attempt == 1 || attempt % LOG_EVERY_N_ATTEMPTS == 0) {
                logService.append(
                        context.getDeploymentId(),
                        LogLevel.DEBUG,
                        LogSource.HEALTH,
                        "Attempt " + attempt + ": " + lastError);
            }

            sleep(interval);
        }

        collectContainerLogs(context);
        throw new StepFailedException(
                "Health check never succeeded within " + timeout.toSeconds() + "s (" + lastError + ")",
                "Confirm the application listens on 0.0.0.0 and on the configured port, and that "
                        + (path.equals("/") ? "/" : path)
                        + " returns a 2xx or 3xx response.");
    }

    /**
     * Where the container might be reachable from wherever DeployForge itself is running.
     *
     * <p>Order matters: the published host port is the documented default, the container name works when
     * the backend shares the application network inside Compose.
     */
    private List<String> candidateUrls(DeploymentContext context, String path) {
        String suffix = path.startsWith("/") ? path : "/" + path;
        List<String> candidates = new ArrayList<>();
        candidates.add(
                "http://" + properties.deployment().publicHost() + ":" + context.getHostPort() + suffix);
        candidates.add("http://" + context.containerName() + ":" + context.getContainerPort() + suffix);
        return candidates;
    }

    private Optional<Integer> probe(String url) {
        try {
            return Optional.of(
                    healthClient
                            .get()
                            .uri(url)
                            .retrieve()
                            .onStatus(status -> true, (request, response) -> {})
                            .toBodilessEntity()
                            .getStatusCode()
                            .value());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private ContainerState inspectContainer(DeploymentContext context) {
        Optional<RuntimeStats> stats = runtime.getStats(context.getContainerId());
        if (stats.isEmpty()) {
            return new ContainerState(false, null, "unknown");
        }
        RuntimeStats current = stats.get();
        boolean exited =
                current.status() != null
                        && (current.status().equalsIgnoreCase("exited")
                                || current.status().equalsIgnoreCase("dead"));
        return new ContainerState(exited, current.exitCode(), current.status());
    }

    /** Pulls the container's own stdout/stderr into the deployment log as failure evidence. */
    private void collectContainerLogs(DeploymentContext context) {
        List<String> lines = runtime.tailLogs(context.getContainerId(), CONTAINER_LOG_LINES);
        if (lines.isEmpty()) {
            logService.append(
                    context.getDeploymentId(),
                    LogLevel.WARN,
                    LogSource.APPLICATION,
                    "The container produced no output at all, which usually means the start command exited immediately.");
            return;
        }
        logService.appendBatch(
                context.getDeploymentId(),
                LogSource.APPLICATION,
                lines.stream()
                        .filter(line -> !line.isBlank())
                        .map(line -> new DeploymentLogService.Line(classify(line), line))
                        .toList());
    }

    private LogLevel classify(String line) {
        String lower = line.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("error") || lower.contains("exception") || lower.contains("fatal")
                ? LogLevel.ERROR
                : LogLevel.INFO;
    }

    private void sleep(Duration interval) {
        try {
            Thread.sleep(interval.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StepCancelledException("Health check interrupted");
        }
    }

    private record ContainerState(boolean exited, Integer exitCode, String status) {}
}
