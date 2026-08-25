package com.deployforge.deployment.pipeline.steps;

import com.deployforge.config.DeployForgeProperties;
import com.deployforge.deployment.DeploymentRepository;
import com.deployforge.deployment.DeploymentStateService;
import com.deployforge.deployment.DeploymentStepName;
import com.deployforge.deployment.pipeline.DeploymentContext;
import com.deployforge.deployment.pipeline.PipelineLogSink;
import com.deployforge.deployment.pipeline.PipelineStep;
import com.deployforge.deployment.pipeline.StepFailedException;
import com.deployforge.environment.EnvironmentVariableService;
import com.deployforge.log.DeploymentLogService;
import com.deployforge.log.LogLevel;
import com.deployforge.log.LogSource;
import com.deployforge.runtime.DeploymentRuntime;
import com.deployforge.runtime.PortAllocator;
import com.deployforge.runtime.RuntimeExceptions.ContainerStartException;
import com.deployforge.runtime.RuntimeExceptions.RuntimeUnavailableException;
import com.deployforge.runtime.RuntimeModels.ContainerRequest;
import com.deployforge.runtime.RuntimeModels.ContainerResult;
import com.deployforge.runtime.docker.DockerLabels;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Starts the application container with the environment injected and resource limits enforced.
 *
 * <p>Notable details:
 *
 * <ul>
 *   <li>Decrypted variable values are registered with the log service <em>before</em> the container
 *       starts, so if the application prints a secret on boot it is redacted on the way in.
 *   <li>{@code PORT} is injected to match the port DeployForge probes and publishes; most frameworks
 *       read it, which removes the most common "works locally, 502 in production" mismatch.
 *   <li>Container stdout/stderr is followed on a virtual thread for a bounded window, so users see
 *       startup output live without a thread pool being held hostage by a long lived stream.
 * </ul>
 */
@Component
public class ContainerStartStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(ContainerStartStep.class);

    private final DeploymentRuntime runtime;
    private final PortAllocator portAllocator;
    private final EnvironmentVariableService variableService;
    private final DeploymentLogService logService;
    private final DeploymentStateService stateService;
    private final DeploymentRepository deploymentRepository;
    private final DeployForgeProperties properties;

    public ContainerStartStep(
            DeploymentRuntime runtime,
            PortAllocator portAllocator,
            EnvironmentVariableService variableService,
            DeploymentLogService logService,
            DeploymentStateService stateService,
            DeploymentRepository deploymentRepository,
            DeployForgeProperties properties) {
        this.runtime = runtime;
        this.portAllocator = portAllocator;
        this.variableService = variableService;
        this.logService = logService;
        this.stateService = stateService;
        this.deploymentRepository = deploymentRepository;
        this.properties = properties;
    }

    @Override
    public DeploymentStepName name() {
        return DeploymentStepName.CONTAINER_START;
    }

    /**
     * Deliberately not transactional.
     *
     * <p>This step decrypts variables, allocates a port, starts a container and appends log lines. Wrapping
     * it in a read-only transaction made every log insert fail with "cannot execute INSERT in a read-only
     * transaction"; wrapping it in a writable one would hold a database connection across a Docker call.
     * Each collaborator manages its own short transaction instead.
     */
    @Override
    public void execute(DeploymentContext context) {
        String imageTag = context.isImageReuse() ? context.getReusableImageTag() : context.getImageTag();
        if (imageTag == null) {
            throw new StepFailedException("No image is available to start");
        }
        if (context.isImageReuse() && !runtime.imageExists(imageTag)) {
            throw new StepFailedException(
                    "Image " + imageTag + " no longer exists on this host",
                    "The image was pruned. Redeploy the commit instead of rolling back to it.");
        }
        context.setImageTag(imageTag);

        try {
            runtime.ensureNetwork();
        } catch (RuntimeUnavailableException e) {
            throw new StepFailedException(
                    "The Docker engine is unavailable: " + e.getMessage(),
                    "Start Docker and check DOCKER_HOST_URI, then redeploy.");
        }

        Set<Integer> claimed = new HashSet<>(deploymentRepository.findClaimedHostPorts());
        int hostPort = portAllocator.allocate(claimed, context.getDeploymentId());
        context.setHostPort(hostPort);

        Map<String, String> environment = resolveEnvironment(context);
        // Register real values so any of them appearing in container output is redacted on ingest.
        logService.registerSecrets(context.getDeploymentId(), environment.values());

        Map<String, String> labels =
                DockerLabels.forDeployment(
                        context.getProjectId(),
                        context.getProjectSlug(),
                        context.getEnvironmentId(),
                        context.getDeploymentId(),
                        context.getDeploymentNumber(),
                        context.getCommitSha());

        // When Traefik routing is active, add Traefik auto-discovery labels so the reverse proxy
        // picks up the container and routes <slug>.<baseDomain> to it.
        if (properties.deployment().isTraefikRouting()) {
            labels.putAll(
                    DockerLabels.traefikLabels(
                            context.getProjectSlug(),
                            context.getContainerPort(),
                            properties.deployment().baseDomain()));
        }

        logService.append(
                context.getDeploymentId(),
                LogLevel.INFO,
                LogSource.DOCKER,
                "Starting container "
                        + context.containerName()
                        + " ("
                        + environment.size()
                        + " environment variables, "
                        + properties.deployment().memoryLimitMb()
                        + "MB memory limit, "
                        + properties.deployment().cpuLimit()
                        + " CPU)");

        try {
            ContainerResult result =
                    runtime.start(
                            new ContainerRequest(
                                    context.containerName(),
                                    imageTag,
                                    environment,
                                    context.getContainerPort(),
                                    hostPort,
                                    labels,
                                    properties.deployment().memoryLimitBytes(),
                                    properties.deployment().cpuLimit(),
                                    properties.deployment().pidsLimit(),
                                    properties.deployment().dockerNetwork()));

            context.setContainerId(result.containerId());
            stateService.recordContainer(context.getDeploymentId(), result.containerId(), hostPort);
            followApplicationLogs(context, result.containerId());

            logService.append(
                    context.getDeploymentId(),
                    LogLevel.INFO,
                    LogSource.DOCKER,
                    "Container started, publishing container port "
                            + context.getContainerPort()
                            + " on host port "
                            + hostPort);

        } catch (ContainerStartException e) {
            portAllocator.releaseLease(hostPort);
            throw new StepFailedException(
                    e.getMessage(),
                    "Check that the exposed port matches the port your application listens on.");
        } catch (RuntimeUnavailableException e) {
            portAllocator.releaseLease(hostPort);
            throw new StepFailedException(
                    "The Docker engine became unavailable while starting the container", null, null, e);
        }
    }

    /**
     * User variables first, then platform variables.
     *
     * <p>Platform values win deliberately: {@code PORT} must agree with the port DeployForge publishes
     * and probes, otherwise the health check fails for a reason that looks like the app's fault.
     */
    private Map<String, String> resolveEnvironment(DeploymentContext context) {
        Map<String, String> environment =
                new LinkedHashMap<>(variableService.resolveForRuntime(context.getEnvironmentId()));
        context.putEnvironmentVariables(environment);

        environment.put("PORT", String.valueOf(context.getContainerPort()));
        environment.put("DEPLOYFORGE", "true");
        environment.put("DEPLOYFORGE_DEPLOYMENT_ID", context.getDeploymentId().toString());
        environment.put("DEPLOYFORGE_DEPLOYMENT_NUMBER", String.valueOf(context.getDeploymentNumber()));
        environment.put("DEPLOYFORGE_PROJECT", context.getProjectSlug());
        if (context.getCommitSha() != null) {
            environment.put("DEPLOYFORGE_COMMIT_SHA", context.getCommitSha());
        }
        environment.put("DEPLOYFORGE_BRANCH", context.getBranch());
        return environment;
    }

    /**
     * Follows container output for a bounded window on a virtual thread.
     *
     * <p>A bounded window rather than "forever": startup output is what users need while a deployment is
     * in progress, and streaming a chatty application's logs into PostgreSQL indefinitely would be a
     * storage leak. Long term application logs are a separate concern from deployment logs.
     */
    private void followApplicationLogs(DeploymentContext context, String containerId) {
        Duration window = properties.deployment().healthCheckTimeout().plus(Duration.ofSeconds(90));
        PipelineLogSink sink =
                new PipelineLogSink(
                        logService,
                        context.getDeploymentId(),
                        LogSource.APPLICATION,
                        context::isCancelled,
                        false);

        Thread.ofVirtual()
                .name("df-applog-" + context.getDeploymentNumber())
                .start(
                        () -> {
                            try {
                                runtime.streamLogs(containerId, sink, window);
                            } catch (RuntimeException e) {
                                log.debug(
                                        "application_log_stream_ended deployment={} reason={}",
                                        context.getDeploymentId(),
                                        e.getMessage());
                            } finally {
                                sink.flush();
                            }
                        });
    }
}
