package com.deployforge.project;

/** Lifecycle of a project. */
public enum ProjectStatus {
    /** Deployments and auto deploy are allowed. */
    ACTIVE,

    /** Kept for reference; auto deploy is ignored and manual deploys are rejected. */
    PAUSED,

    /** Read only. Containers are stopped and no deployment can be created. */
    ARCHIVED
}
