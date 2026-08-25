package com.deployforge.deployment.queue;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Hand-off between the API and the deployment workers.
 *
 * <p>Deliberately a small interface instead of a full job framework. What DeployForge actually needs is
 * a durable FIFO, a cancellation flag and a per-environment mutex; wiring in a scheduler library would
 * add operational surface without answering any of those three questions better.
 *
 * <p>Implementations must be safe for multiple workers and multiple application instances.
 */
public interface DeploymentQueue {

    /** Appends a deployment for processing. Idempotent enough that a duplicate enqueue is harmless. */
    void enqueue(UUID deploymentId);

    /** Blocks up to {@code timeout} for the next deployment id. */
    Optional<UUID> poll(Duration timeout);

    /** Puts a deployment back at the tail, used when its environment is busy. */
    void requeue(UUID deploymentId);

    long depth();

    /** Marks a deployment as cancelled; running steps observe this between operations. */
    void requestCancellation(UUID deploymentId);

    boolean isCancellationRequested(UUID deploymentId);

    void clearCancellation(UUID deploymentId);

    /**
     * Serialises deployments per environment: two deployments must never race to own the same
     * environment's container and port.
     *
     * @return true when this deployment now owns the environment
     */
    boolean tryAcquireEnvironmentLock(UUID environmentId, UUID deploymentId, Duration ttl);

    /** Releases the lock only if it is still held by {@code deploymentId}. */
    void releaseEnvironmentLock(UUID environmentId, UUID deploymentId);

    Optional<UUID> environmentLockHolder(UUID environmentId);
}
