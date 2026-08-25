package com.deployforge.runtime;

import com.deployforge.runtime.RuntimeModels.BuildRequest;
import com.deployforge.runtime.RuntimeModels.BuildResult;
import com.deployforge.runtime.RuntimeModels.ContainerRequest;
import com.deployforge.runtime.RuntimeModels.ContainerResult;
import com.deployforge.runtime.RuntimeModels.RuntimeStats;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The boundary between DeployForge's domain logic and whatever actually runs containers.
 *
 * <p>The only implementation today is {@code DockerDeploymentRuntime}, talking to a local Docker
 * engine (single node architecture). A {@code KubernetesDeploymentRuntime} or a remote build worker
 * can be introduced later without the deployment pipeline, the project domain or the API layer
 * knowing about it - which is the entire point of this interface.
 *
 * <p>Implementations must be safe to call concurrently and must never accept a raw shell string:
 * all process invocation happens through structured arguments.
 */
public interface DeploymentRuntime {

    /** Cheap probe used by the health endpoint. Must not throw. */
    RuntimeAvailability checkAvailability();

    /**
     * Builds an image from a prepared build context.
     *
     * @throws RuntimeExceptions.BuildFailedException when the build exits non-zero
     * @throws RuntimeExceptions.OperationTimedOutException when the build exceeds its budget
     * @throws RuntimeExceptions.RuntimeUnavailableException when the engine is unreachable
     */
    BuildResult build(BuildRequest request);

    /** Creates and starts a container. */
    ContainerResult start(ContainerRequest request);

    /** Stops a container, allowing {@code timeout} for a graceful shutdown. Idempotent. */
    void stop(String containerId, Duration timeout);

    /** Removes a container. Idempotent: a missing container is not an error. */
    void remove(String containerId);

    /** Latest resource usage sample, empty when the container is gone. */
    Optional<RuntimeStats> getStats(String containerId);

    /** Last {@code lines} lines of container stdout/stderr. Used for failure analysis. */
    List<String> tailLogs(String containerId, int lines);

    /**
     * Streams container output until {@code follow} completes or the container exits. Blocking; call
     * it from a worker thread.
     */
    void streamLogs(String containerId, LogSink sink, Duration maxDuration);

    boolean imageExists(String imageTag);

    /** Removes an image previously built by DeployForge. Idempotent. */
    void removeImage(String imageTag);

    /** Container ids managed by DeployForge, optionally filtered by label. */
    List<ManagedContainer> listManagedContainers();

    /** Image tags managed by DeployForge. */
    List<String> listManagedImages();

    /** Ensures the isolated application network exists. */
    void ensureNetwork();

    record RuntimeAvailability(boolean available, String detail, String version) {
        public static RuntimeAvailability up(String version) {
            return new RuntimeAvailability(true, "Docker engine " + version + " reachable", version);
        }

        public static RuntimeAvailability down(String detail) {
            return new RuntimeAvailability(false, detail, null);
        }
    }

    record ManagedContainer(
            String containerId,
            String name,
            String status,
            String projectId,
            String deploymentId,
            String imageTag) {}
}
