package com.deployforge.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for every error the API deliberately surfaces to clients.
 *
 * <p>Carries an {@link ErrorCode} and an HTTP status so {@code GlobalExceptionHandler} never has to
 * guess, and so no stack trace or internal detail leaks into a response body.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;

    public ApiException(ErrorCode code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public ApiException(ErrorCode code, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
