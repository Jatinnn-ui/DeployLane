package com.deployforge.runtime;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Value objects exchanged with a {@link DeploymentRuntime}. */
public final class RuntimeModels {

    private RuntimeModels() {}

    /**
     * A build request.
     *
     * @param contextDirectory absolute path of the prepared build context (already validated to live
     *     inside the deployment root)
     * @param dockerfile absolute path of the Dockerfile to use; may live outside the context when
     *     DeployForge generated it
     * @param labels Docker labels, always including {@code deployforge.managed=true} so cleanup can
     *     tell our resources apart from everything else on the host
     */
    public record BuildRequest(
            String imageTag,
            Path contextDirectory,
            Path dockerfile,
            Map<String, String> buildArgs,
            Map<String, String> labels,
            Duration timeout,
            LogSink logSink) {}

    public record BuildResult(String imageTag, String imageId, long durationMs) {}

    /**
     * A container start request.
     *
     * <p>Resource limits are mandatory, not optional: user supplied code runs in this container.
     */
    public record ContainerRequest(
            String containerName,
            String imageTag,
            Map<String, String> environment,
            int containerPort,
            int hostPort,
            Map<String, String> labels,
            long memoryLimitBytes,
            double cpuLimit,
            long pidsLimit,
            String network) {}

    public record ContainerResult(String containerId, String containerName, int hostPort) {}

    public record RuntimeStats(
            String containerId,
            String status,
            double cpuPercent,
            long memoryBytes,
            long memoryLimitBytes,
            int restartCount,
            Instant startedAt,
            Integer exitCode) {

        public double memoryPercent() {
            return memoryLimitBytes <= 0 ? 0d : (memoryBytes * 100d) / memoryLimitBytes;
        }
    }
}
