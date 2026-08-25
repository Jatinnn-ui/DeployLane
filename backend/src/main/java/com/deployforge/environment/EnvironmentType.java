package com.deployforge.environment;

/** The kind of environment a deployment targets. */
public enum EnvironmentType {
    /** The live environment. Gets zero-downtime promotion and is what a project's URL points at. */
    PRODUCTION,

    /** Ephemeral environment created for a pull request. */
    PREVIEW,

    /** Long lived non production environment. */
    DEVELOPMENT
}
