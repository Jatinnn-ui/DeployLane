package com.deployforge.runtime.docker;

import com.deployforge.config.DeployForgeProperties;
import com.deployforge.runtime.DeploymentRuntime;
import com.deployforge.runtime.LogSink;
import com.deployforge.runtime.RuntimeExceptions.BuildFailedException;
import com.deployforge.runtime.RuntimeExceptions.ContainerStartException;
import com.deployforge.runtime.RuntimeExceptions.OperationCancelledException;
import com.deployforge.runtime.RuntimeExceptions.OperationTimedOutException;
import com.deployforge.runtime.RuntimeExceptions.RuntimeUnavailableException;
import com.deployforge.runtime.RuntimeModels.BuildRequest;
import com.deployforge.runtime.RuntimeModels.BuildResult;
import com.deployforge.runtime.RuntimeModels.ContainerRequest;
import com.deployforge.runtime.RuntimeModels.ContainerResult;
import com.deployforge.runtime.RuntimeModels.RuntimeStats;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.api.model.Statistics;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link DeploymentRuntime} backed by a local Docker engine.
 *
 * <p>Security posture for user supplied code (see {@code docs/security.md}):
 *
 * <ul>
 *   <li>never privileged, {@code no-new-privileges} always set
 *   <li>no host filesystem bind mounts, and never the Docker socket
 *   <li>hard memory, CPU and PID limits on every container
 *   <li>dangerous capabilities dropped ({@code SYS_ADMIN}, {@code NET_RAW}, {@code SYS_PTRACE}, ...)
 *   <li>attached to a dedicated bridge network, never host networking
 *   <li>builds are time boxed and cancellable
 * </ul>
 *
 * <p>Nothing here shells out. Every operation is a typed Docker Engine API call, so there is no
 * command string for user input to break out of.
 */
@Component
public class DockerDeploymentRuntime implements DeploymentRuntime {

    private static final Logger log = LoggerFactory.getLogger(DockerDeploymentRuntime.class);

    private static final Capability[] DROPPED_CAPABILITIES = {
        Capability.SYS_ADMIN,
        Capability.SYS_MODULE,
        Capability.SYS_PTRACE,
        Capability.SYS_RAWIO,
        Capability.NET_RAW,
        Capability.MKNOD,
        Capability.AUDIT_WRITE
    };

    private final DockerClientFactory clientFactory;
    private final DeployForgeProperties properties;

    /** Previous CPU counters per container, needed to turn cumulative values into a percentage. */
    private final Map<String, CpuSample> cpuSamples = new ConcurrentHashMap<>();

    public DockerDeploymentRuntime(
            DockerClientFactory clientFactory, DeployForgeProperties properties) {
        this.clientFactory = clientFactory;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ availability

    @Override
    public RuntimeAvailability checkAvailability() {
        try {
            var version = clientFactory.client().versionCmd().exec();
            return RuntimeAvailability.up(
                    version.getVersion() == null ? "unknown" : version.getVersion());
        } catch (RuntimeException e) {
            String detail = rootMessage(e);
            log.warn(
                    "docker_unavailable host={} reason={}",
                    properties.deployment().dockerHostUri(),
                    detail);
            return RuntimeAvailability.down(
                    "Docker engine unreachable at "
                            + properties.deployment().dockerHostUri()
                            + " ("
                            + detail
                            + ")");
        }
    }

    @Override
    public void ensureNetwork() {
        String name = properties.deployment().dockerNetwork();
        try {
            List<Network> networks = client().listNetworksCmd().withNameFilter(name).exec();
            if (networks.stream().anyMatch(network -> name.equals(network.getName()))) {
                return;
            }
            client()
                    .createNetworkCmd()
                    .withName(name)
                    .withDriver("bridge")
                    .withLabels(DockerLabels.managedFilter())
                    .exec();
            log.info("docker_network_created name={}", name);
        } catch (com.github.dockerjava.api.exception.ConflictException e) {
            // Created concurrently by another worker - that is the desired end state anyway.
        } catch (DockerException e) {
            throw new RuntimeUnavailableException("Could not ensure Docker network '" + name + "'", e);
        }
    }

    // ------------------------------------------------------------------ build

    @Override
    public BuildResult build(BuildRequest request) {
        long start = System.nanoTime();
        AtomicReference<String> errorDetail = new AtomicReference<>();
        LogSink sink = request.logSink();

        BuildImageResultCallback callback =
                new BuildImageResultCallback() {
                    @Override
                    public void onNext(BuildResponseItem item) {
                        if (sink != null) {
                            if (item.getStream() != null) {
                                emitLines(sink, item.getStream(), false);
                            }
                            if (item.getErrorDetail() != null && item.getErrorDetail().getMessage() != null) {
                                emitLines(sink, item.getErrorDetail().getMessage(), true);
                            }
                        }
                        if (item.isErrorIndicated()) {
                            errorDetail.set(
                                    item.getErrorDetail() == null || item.getErrorDetail().getMessage() == null
                                            ? "docker build reported an error"
                                            : item.getErrorDetail().getMessage().trim());
                        }
                        if (sink != null && sink.cancelled()) {
                            try {
                                close();
                            } catch (IOException ignored) {
                                // Already tearing the build down; nothing useful to do here.
                            }
                        }
                        super.onNext(item);
                    }
                };

        try {
            var command =
                    client()
                            .buildImageCmd()
                            .withBaseDirectory(request.contextDirectory().toFile())
                            .withDockerfile(request.dockerfile().toFile())
                            .withTags(Set.of(request.imageTag()))
                            .withLabels(request.labels())
                            .withPull(false)
                            .withNoCache(false)
                            .withRemove(true);

            if (request.buildArgs() != null) {
                request.buildArgs().forEach(command::withBuildArg);
            }

            String imageId =
                    command.exec(callback).awaitImageId(request.timeout().toSeconds(), TimeUnit.SECONDS);

            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.info(
                    "docker_image_built tag={} image={} duration_ms={}",
                    request.imageTag(),
                    shortId(imageId),
                    durationMs);
            return new BuildResult(request.imageTag(), imageId, durationMs);

        } catch (DockerClientException e) {
            // docker-java surfaces a failed build as DockerClientException; the useful text came
            // through the stream, which is why it was captured above.
            if (sink != null && sink.cancelled()) {
                throw new OperationCancelledException("Image build cancelled");
            }
            if (isTimeout(e)) {
                throw new OperationTimedOutException(
                        "Image build exceeded its " + request.timeout().toSeconds() + "s budget");
            }
            String detail = errorDetail.get() != null ? errorDetail.get() : rootMessage(e);
            throw new BuildFailedException("Image build failed: " + detail, extractExitCode(detail));
        } catch (DockerException e) {
            throw new RuntimeUnavailableException("Docker engine rejected the build request", e);
        } catch (RuntimeException e) {
            if (isTimeout(e)) {
                throw new OperationTimedOutException(
                        "Image build exceeded its " + request.timeout().toSeconds() + "s budget");
            }
            String detail = errorDetail.get() != null ? errorDetail.get() : rootMessage(e);
            throw new BuildFailedException("Image build failed: " + detail, extractExitCode(detail));
        } finally {
            closeQuietly(callback);
        }
    }

    // ------------------------------------------------------------------ containers

    @Override
    public ContainerResult start(ContainerRequest request) {
        ExposedPort exposedPort = ExposedPort.tcp(request.containerPort());

        HostConfig hostConfig =
                HostConfig.newHostConfig()
                        .withPortBindings(
                                new PortBinding(Ports.Binding.bindPort(request.hostPort()), exposedPort))
                        .withMemory(request.memoryLimitBytes())
                        // Equal memory and swap: the container cannot escape its limit via swap.
                        .withMemorySwap(request.memoryLimitBytes())
                        .withNanoCPUs((long) (request.cpuLimit() * 1_000_000_000L))
                        .withPidsLimit(request.pidsLimit())
                        .withNetworkMode(request.network())
                        .withPrivileged(false)
                        .withCapDrop(DROPPED_CAPABILITIES)
                        .withSecurityOpts(List.of("no-new-privileges:true"))
                        .withRestartPolicy(RestartPolicy.onFailureRestart(3));

        List<String> environment =
                request.environment().entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .toList();

        try {
            removeExistingByName(request.containerName());

            CreateContainerResponse created =
                    client()
                            .createContainerCmd(request.imageTag())
                            .withName(request.containerName())
                            .withEnv(environment)
                            .withExposedPorts(exposedPort)
                            .withLabels(request.labels())
                            .withHostConfig(hostConfig)
                            .exec();

            client().startContainerCmd(created.getId()).exec();
            log.info(
                    "container_started name={} id={} host_port={} container_port={}",
                    request.containerName(),
                    shortId(created.getId()),
                    request.hostPort(),
                    request.containerPort());
            return new ContainerResult(created.getId(), request.containerName(), request.hostPort());

        } catch (NotFoundException e) {
            throw new ContainerStartException(
                    "Image " + request.imageTag() + " was not found when creating the container", e);
        } catch (com.github.dockerjava.api.exception.ConflictException e) {
            throw new ContainerStartException(
                    "A container named " + request.containerName() + " already exists", e);
        } catch (DockerException e) {
            String message = rootMessage(e);
            if (message.contains("port is already allocated") || message.contains("address already in use")) {
                throw new ContainerStartException(
                        "Host port " + request.hostPort() + " is already in use", e);
            }
            throw new ContainerStartException("Container could not be started: " + message, e);
        }
    }

    @Override
    public void stop(String containerId, Duration timeout) {
        if (isBlank(containerId)) {
            return;
        }
        try {
            client()
                    .stopContainerCmd(containerId)
                    .withTimeout((int) Math.max(1, timeout.toSeconds()))
                    .exec();
            log.info("container_stopped id={}", shortId(containerId));
        } catch (NotFoundException | NotModifiedException e) {
            // Already gone or already stopped: stop() is idempotent by contract.
        } catch (DockerException e) {
            log.warn("container_stop_failed id={} reason={}", shortId(containerId), rootMessage(e));
        }
    }

    @Override
    public void remove(String containerId) {
        if (isBlank(containerId)) {
            return;
        }
        try {
            client().removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec();
            cpuSamples.remove(containerId);
            log.info("container_removed id={}", shortId(containerId));
        } catch (NotFoundException e) {
            // Idempotent.
        } catch (DockerException e) {
            log.warn("container_remove_failed id={} reason={}", shortId(containerId), rootMessage(e));
        }
    }

    private void removeExistingByName(String containerName) {
        try {
            List<Container> existing =
                    client()
                            .listContainersCmd()
                            .withShowAll(true)
                            .withNameFilter(List.of(containerName))
                            .exec();
            for (Container container : existing) {
                boolean exactMatch =
                        container.getNames() != null
                                && Arrays.stream(container.getNames())
                                        .map(name -> name.startsWith("/") ? name.substring(1) : name)
                                        .anyMatch(containerName::equals);
                if (exactMatch) {
                    log.info(
                            "container_replacing_stale name={} id={}",
                            containerName,
                            shortId(container.getId()));
                    remove(container.getId());
                }
            }
        } catch (DockerException e) {
            log.debug("container_stale_check_failed name={} reason={}", containerName, rootMessage(e));
        }
    }

    // ------------------------------------------------------------------ stats

    @Override
    public Optional<RuntimeStats> getStats(String containerId) {
        if (isBlank(containerId)) {
            return Optional.empty();
        }
        InspectContainerResponse inspect;
        try {
            inspect = client().inspectContainerCmd(containerId).exec();
        } catch (NotFoundException e) {
            cpuSamples.remove(containerId);
            return Optional.empty();
        } catch (DockerException e) {
            log.debug("container_inspect_failed id={} reason={}", shortId(containerId), rootMessage(e));
            return Optional.empty();
        }

        String status = inspect.getState() == null ? "unknown" : inspect.getState().getStatus();
        Integer exitCode =
                inspect.getState() == null || inspect.getState().getExitCodeLong() == null
                        ? null
                        : inspect.getState().getExitCodeLong().intValue();
        int restartCount = inspect.getRestartCount() == null ? 0 : inspect.getRestartCount();
        Instant startedAt =
                parseInstant(inspect.getState() == null ? null : inspect.getState().getStartedAt());

        long memoryLimit = properties.deployment().memoryLimitBytes();
        long memoryBytes = 0L;
        double cpuPercent = 0d;

        Statistics statistics = sampleStatistics(containerId);
        if (statistics != null) {
            memoryBytes = memoryUsage(statistics);
            Long reportedLimit =
                    statistics.getMemoryStats() == null ? null : statistics.getMemoryStats().getLimit();
            if (reportedLimit != null && reportedLimit > 0) {
                memoryLimit = Math.min(reportedLimit, properties.deployment().memoryLimitBytes());
            }
            cpuPercent = cpuPercent(containerId, statistics);
        }

        return Optional.of(
                new RuntimeStats(
                        containerId,
                        status,
                        cpuPercent,
                        memoryBytes,
                        memoryLimit,
                        restartCount,
                        startedAt,
                        exitCode));
    }

    private Statistics sampleStatistics(String containerId) {
        AtomicReference<Statistics> holder = new AtomicReference<>();
        try (ResultCallback.Adapter<Statistics> callback =
                client()
                        .statsCmd(containerId)
                        .withNoStream(true)
                        .exec(
                                new ResultCallback.Adapter<>() {
                                    @Override
                                    public void onNext(Statistics statistics) {
                                        holder.compareAndSet(null, statistics);
                                    }
                                })) {
            callback.awaitCompletion(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException | IOException e) {
            log.debug("container_stats_failed id={} reason={}", shortId(containerId), rootMessage(e));
        }
        return holder.get();
    }

    private long memoryUsage(Statistics statistics) {
        if (statistics.getMemoryStats() == null || statistics.getMemoryStats().getUsage() == null) {
            return 0L;
        }
        long usage = statistics.getMemoryStats().getUsage();
        // Page cache is reclaimable and inflates "usage", so subtract it when reported.
        var raw = statistics.getMemoryStats().getStats();
        if (raw != null && raw.getCache() != null) {
            usage = Math.max(0L, usage - raw.getCache());
        }
        return usage;
    }

    /**
     * CPU percentage derived from two consecutive cumulative samples.
     *
     * <p>The one-shot stats endpoint does not reliably populate {@code precpu_stats}, so the previous
     * sample is kept per container and the delta is computed here. The first sample after a start
     * reports 0%, which is honest rather than wrong.
     */
    private double cpuPercent(String containerId, Statistics statistics) {
        if (statistics.getCpuStats() == null
                || statistics.getCpuStats().getCpuUsage() == null
                || statistics.getCpuStats().getCpuUsage().getTotalUsage() == null
                || statistics.getCpuStats().getSystemCpuUsage() == null) {
            return 0d;
        }
        long total = statistics.getCpuStats().getCpuUsage().getTotalUsage();
        long system = statistics.getCpuStats().getSystemCpuUsage();
        long cpus =
                statistics.getCpuStats().getOnlineCpus() == null
                        ? Math.max(1, Runtime.getRuntime().availableProcessors())
                        : statistics.getCpuStats().getOnlineCpus();

        CpuSample previous = cpuSamples.put(containerId, new CpuSample(total, system));
        if (previous == null) {
            return 0d;
        }
        long totalDelta = total - previous.total();
        long systemDelta = system - previous.system();
        if (totalDelta <= 0 || systemDelta <= 0) {
            return 0d;
        }
        double percent = ((double) totalDelta / (double) systemDelta) * cpus * 100d;
        return Math.min(Math.round(percent * 10d) / 10d, cpus * 100d);
    }

    // ------------------------------------------------------------------ logs

    @Override
    public List<String> tailLogs(String containerId, int lines) {
        if (isBlank(containerId)) {
            return List.of();
        }
        List<String> collected = new ArrayList<>();
        try (ResultCallback.Adapter<Frame> callback =
                client()
                        .logContainerCmd(containerId)
                        .withStdOut(true)
                        .withStdErr(true)
                        .withTail(Math.max(1, lines))
                        .withTimestamps(false)
                        .exec(
                                new ResultCallback.Adapter<>() {
                                    @Override
                                    public void onNext(Frame frame) {
                                        new String(frame.getPayload(), StandardCharsets.UTF_8)
                                                .lines()
                                                .forEach(collected::add);
                                    }
                                })) {
            callback.awaitCompletion(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (NotFoundException e) {
            return List.of();
        } catch (RuntimeException | IOException e) {
            log.debug("container_logs_failed id={} reason={}", shortId(containerId), rootMessage(e));
        }
        return collected;
    }

    /**
     * Follows container output until the container exits, {@code maxDuration} elapses, or the sink asks
     * to stop. Blocking: the caller runs it on a worker thread.
     */
    @Override
    public void streamLogs(String containerId, LogSink sink, Duration maxDuration) {
        if (isBlank(containerId) || sink == null) {
            return;
        }
        try (ResultCallback.Adapter<Frame> callback =
                client()
                        .logContainerCmd(containerId)
                        .withStdOut(true)
                        .withStdErr(true)
                        .withFollowStream(true)
                        .withTail(50)
                        .exec(
                                new ResultCallback.Adapter<>() {
                                    @Override
                                    public void onNext(Frame frame) {
                                        boolean isError =
                                                frame.getStreamType() != null
                                                        && "STDERR".equals(frame.getStreamType().name());
                                        emitLines(
                                                sink,
                                                new String(frame.getPayload(), StandardCharsets.UTF_8),
                                                isError);
                                        if (sink.cancelled()) {
                                            try {
                                                close();
                                            } catch (IOException ignored) {
                                                // Stream is being torn down anyway.
                                            }
                                        }
                                    }
                                })) {
            callback.awaitCompletion(maxDuration.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (NotFoundException e) {
            // Container disappeared while we were attaching; nothing left to stream.
        } catch (RuntimeException | IOException e) {
            log.debug("container_log_stream_failed id={} reason={}", shortId(containerId), rootMessage(e));
        }
    }

    // ------------------------------------------------------------------ images

    @Override
    public boolean imageExists(String imageTag) {
        if (isBlank(imageTag)) {
            return false;
        }
        try {
            client().inspectImageCmd(imageTag).exec();
            return true;
        } catch (NotFoundException e) {
            return false;
        } catch (DockerException e) {
            log.debug("image_inspect_failed tag={} reason={}", imageTag, rootMessage(e));
            return false;
        }
    }

    @Override
    public void removeImage(String imageTag) {
        if (isBlank(imageTag)) {
            return;
        }
        try {
            client().removeImageCmd(imageTag).withForce(true).exec();
            log.info("image_removed tag={}", imageTag);
        } catch (NotFoundException e) {
            // Idempotent.
        } catch (com.github.dockerjava.api.exception.ConflictException e) {
            log.debug("image_still_in_use tag={}", imageTag);
        } catch (DockerException e) {
            log.warn("image_remove_failed tag={} reason={}", imageTag, rootMessage(e));
        }
    }

    @Override
    public List<ManagedContainer> listManagedContainers() {
        try {
            return client().listContainersCmd().withShowAll(true)
                    .withLabelFilter(DockerLabels.managedFilter())
                    .exec()
                    .stream()
                    .map(
                            container ->
                                    new ManagedContainer(
                                            container.getId(),
                                            container.getNames() == null || container.getNames().length == 0
                                                    ? ""
                                                    : container.getNames()[0].replaceFirst("^/", ""),
                                            container.getState(),
                                            label(container.getLabels(), DockerLabels.PROJECT_ID),
                                            label(container.getLabels(), DockerLabels.DEPLOYMENT_ID),
                                            container.getImage()))
                    .toList();
        } catch (DockerException e) {
            log.warn("managed_container_list_failed reason={}", rootMessage(e));
            return List.of();
        }
    }

    @Override
    public List<String> listManagedImages() {
        try {
            List<String> tags = new ArrayList<>();
            for (Image image :
                    client().listImagesCmd().withLabelFilter(DockerLabels.managedFilter()).exec()) {
                if (image.getRepoTags() != null) {
                    tags.addAll(Arrays.asList(image.getRepoTags()));
                }
            }
            return tags;
        } catch (DockerException e) {
            log.warn("managed_image_list_failed reason={}", rootMessage(e));
            return List.of();
        }
    }

    // ------------------------------------------------------------------ helpers

    private DockerClient client() {
        return clientFactory.client();
    }

    private static void emitLines(LogSink sink, String chunk, boolean error) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        chunk.lines()
                .map(line -> line.stripTrailing())
                .filter(line -> !line.isBlank())
                .forEach(line -> sink.accept(error ? LogSink.LogLine.err(line) : LogSink.LogLine.out(line)));
    }

    private static String label(Map<String, String> labels, String key) {
        return labels == null ? null : labels.get(key);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String shortId(String id) {
        if (id == null) {
            return "none";
        }
        return id.length() > 12 ? id.substring(0, 12) : id;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank() || value.startsWith("0001-01-01")) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private boolean isTimeout(RuntimeException e) {
        String message = rootMessage(e).toLowerCase(java.util.Locale.ROOT);
        return message.contains("timeout") || message.contains("timed out");
    }

    /** Build failures often end with {@code exit code: 1}; surfacing it helps the AI analyzer. */
    private Integer extractExitCode(String detail) {
        if (detail == null) {
            return null;
        }
        var matcher = java.util.regex.Pattern.compile("exit code:?\\s*(\\d{1,3})").matcher(detail);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }
        String single = message.replaceAll("\\R+", " ").trim();
        return single.length() > 400 ? single.substring(0, 400) + "..." : single;
    }

    private void closeQuietly(java.io.Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            log.debug("docker_callback_close_failed reason={}", e.getMessage());
        }
    }

    private record CpuSample(long total, long system) {}
}
