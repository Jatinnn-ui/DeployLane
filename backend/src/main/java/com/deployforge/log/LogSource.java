package com.deployforge.log;

/**
 * Where a log line came from.
 *
 * <p>Keeping the origin explicit is what makes the log viewer usable: filtering to {@code BUILD} or
 * {@code APPLICATION} is the difference between reading a wall of text and finding the failure.
 */
public enum LogSource {
    /** DeployForge's own pipeline narration. */
    SYSTEM,
    /** git clone / checkout output. */
    GIT,
    /** Install and build command output. */
    BUILD,
    /** Docker image build and container lifecycle output. */
    DOCKER,
    /** stdout/stderr of the running application container. */
    APPLICATION,
    /** Health probe results. */
    HEALTH,
    /** AI analysis narration. */
    AI
}
