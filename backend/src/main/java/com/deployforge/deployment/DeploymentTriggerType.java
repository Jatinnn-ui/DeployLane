package com.deployforge.deployment;

/** Why a deployment exists. Shown in the history list and used by rate limiting and auditing. */
public enum DeploymentTriggerType {
    /** A person pressed Deploy. */
    MANUAL,

    /** A GitHub push webhook matched an environment with auto deploy enabled. */
    GIT_PUSH,

    /** A pull request event created a preview deployment. */
    PULL_REQUEST,

    /** Re-running a previous successful deployment's image. */
    ROLLBACK,

    /** Re-building the same commit with the current configuration. */
    REDEPLOY
}
