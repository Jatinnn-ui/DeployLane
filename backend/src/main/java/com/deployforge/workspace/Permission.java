package com.deployforge.workspace;

/** Fine grained capabilities checked by {@link AuthorizationService}. */
public enum Permission {
    VIEW_PROJECT,
    VIEW_LOGS,
    VIEW_MONITORING,
    VIEW_ANALYSIS,

    DEPLOY,
    CANCEL_DEPLOYMENT,
    ROLLBACK,

    MANAGE_PROJECT,
    DELETE_PROJECT,
    MANAGE_ENVIRONMENT,
    MANAGE_VARIABLES,

    /** Triggering AI work costs money and quota, so it is not granted to viewers. */
    USE_AI,

    MANAGE_MEMBERS,
    MANAGE_WORKSPACE,
    DELETE_WORKSPACE
}
