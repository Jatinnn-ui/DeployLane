package com.deployforge.deployment;

/** The fixed stages of the pipeline, in execution order. */
public enum DeploymentStepName {
    QUEUE("Queued", DeploymentStatus.QUEUED),
    CLONE("Repository cloned", DeploymentStatus.CLONING),
    DETECT("Framework detected", DeploymentStatus.DETECTING),
    /** Writes the build plan: generated Dockerfile, ignore file, resolved commands. */
    BUILD("Build plan prepared", DeploymentStatus.BUILDING),
    /** Dependencies install and the application build run inside this image build. */
    IMAGE_BUILD("Application built and image created", DeploymentStatus.IMAGE_BUILDING),
    CONTAINER_START("Container started", DeploymentStatus.STARTING),
    HEALTH_CHECK("Health check passed", DeploymentStatus.HEALTH_CHECKING),
    FINALIZE("Deployment ready", DeploymentStatus.READY);

    private final String label;
    private final DeploymentStatus status;

    DeploymentStepName(String label, DeploymentStatus status) {
        this.label = label;
        this.status = status;
    }

    public String label() {
        return label;
    }

    /** The deployment status this step runs under. */
    public DeploymentStatus status() {
        return status;
    }

    public int sequence() {
        return ordinal();
    }
}
