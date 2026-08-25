package com.deployforge.common.error;

/**
 * Stable, machine readable error codes returned to API clients.
 *
 * <p>The frontend switches on these, never on human readable messages.
 */
public enum ErrorCode {
    VALIDATION_FAILED,
    UNAUTHENTICATED,
    SESSION_EXPIRED,
    ACCESS_DENIED,
    NOT_FOUND,
    CONFLICT,
    RATE_LIMITED,

    GITHUB_NOT_CONNECTED,
    GITHUB_API_ERROR,
    GITHUB_OAUTH_ERROR,
    WEBHOOK_SIGNATURE_INVALID,
    WEBHOOK_NOT_CONFIGURED,

    INVALID_DEPLOYMENT_STATE,
    DEPLOYMENT_IN_PROGRESS,
    DEPLOYMENT_FAILED,
    ROLLBACK_NOT_POSSIBLE,

    FRAMEWORK_DETECTION_FAILED,
    RUNTIME_UNAVAILABLE,
    DOCKER_UNAVAILABLE,
    AI_UNAVAILABLE,
    ENCRYPTION_ERROR,

    INTERNAL_ERROR
}
