package com.deployforge.common.error;

import org.springframework.http.HttpStatus;

/** Concrete {@link ApiException} subtypes, grouped to keep the package tidy. */
public final class Exceptions {

    private Exceptions() {}

    /** 404 - the resource does not exist, or the caller may not know that it exists. */
    public static class NotFoundException extends ApiException {
        public NotFoundException(String message) {
            super(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, message);
        }

        public static NotFoundException of(String resource, Object id) {
            return new NotFoundException(resource + " " + id + " was not found");
        }
    }

    /** 400 - semantic validation failure raised from the service layer. */
    public static class BadRequestException extends ApiException {
        public BadRequestException(String message) {
            super(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, message);
        }

        public BadRequestException(ErrorCode code, String message) {
            super(code, HttpStatus.BAD_REQUEST, message);
        }
    }

    /** 409 - the request conflicts with current state (duplicate slug, concurrent deploy, ...). */
    public static class ConflictException extends ApiException {
        public ConflictException(String message) {
            super(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
        }

        public ConflictException(ErrorCode code, String message) {
            super(code, HttpStatus.CONFLICT, message);
        }
    }

    /** 409 - an operation was attempted from an illegal deployment state. */
    public static class InvalidStateException extends ApiException {
        public InvalidStateException(String message) {
            super(ErrorCode.INVALID_DEPLOYMENT_STATE, HttpStatus.CONFLICT, message);
        }

        public InvalidStateException(ErrorCode code, String message) {
            super(code, HttpStatus.CONFLICT, message);
        }
    }

    /** 401 - no or invalid credentials. */
    public static class UnauthenticatedException extends ApiException {
        public UnauthenticatedException(String message) {
            super(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, message);
        }

        public UnauthenticatedException(ErrorCode code, String message) {
            super(code, HttpStatus.UNAUTHORIZED, message);
        }
    }

    /** 403 - authenticated, but the workspace role does not allow this action. */
    public static class AccessDeniedException extends ApiException {
        public AccessDeniedException(String message) {
            super(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, message);
        }
    }

    /** 429 - rate limit exceeded. */
    public static class RateLimitedException extends ApiException {
        public RateLimitedException(String message) {
            super(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }

    /**
     * 502 - a dependency we do not control failed (GitHub, Docker engine, AI provider). Always wrap
     * the original cause; never swallow it.
     */
    public static class ExternalServiceException extends ApiException {
        public ExternalServiceException(ErrorCode code, String message) {
            super(code, HttpStatus.BAD_GATEWAY, message);
        }

        public ExternalServiceException(ErrorCode code, String message, Throwable cause) {
            super(code, HttpStatus.BAD_GATEWAY, message, cause);
        }
    }
}
