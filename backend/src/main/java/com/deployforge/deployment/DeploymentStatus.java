package com.deployforge.deployment;

import java.util.Set;

/**
 * Lifecycle of a single deployment.
 *
 * <p>The happy path is linear: {@code QUEUED -> CLONING -> DETECTING -> BUILDING -> IMAGE_BUILDING ->
 * STARTING -> HEALTH_CHECKING -> READY}. Any non terminal state can fall to {@code FAILED} or
 * {@code CANCELLED}. Allowed transitions are enforced centrally by {@link DeploymentStateMachine}.
 */
public enum DeploymentStatus {
    QUEUED("Queued"),
    CLONING("Cloning repository"),
    DETECTING("Detecting framework"),
    BUILDING("Building application"),
    IMAGE_BUILDING("Building image"),
    STARTING("Starting container"),
    HEALTH_CHECKING("Health checking"),
    READY("Ready"),
    FAILED("Failed"),
    CANCELLED("Cancelled"),
    STOPPED("Stopped");

    private static final Set<DeploymentStatus> TERMINAL =
            Set.of(READY, FAILED, CANCELLED, STOPPED);

    private static final Set<DeploymentStatus> CANCELLABLE =
            Set.of(QUEUED, CLONING, DETECTING, BUILDING, IMAGE_BUILDING, STARTING, HEALTH_CHECKING);

    private final String label;

    DeploymentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** No further automatic progress will happen. */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** The pipeline is (or should be) working on it. */
    public boolean isActive() {
        return !isTerminal();
    }

    public boolean isCancellable() {
        return CANCELLABLE.contains(this);
    }

    public boolean isFailure() {
        return this == FAILED;
    }
}
