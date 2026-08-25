package com.deployforge.common.error;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The single error envelope for every non 2xx API response.
 *
 * <pre>
 * {
 *   "timestamp": "2026-02-11T12:43:21.884Z",
 *   "status": 409,
 *   "code": "INVALID_DEPLOYMENT_STATE",
 *   "message": "Only READY deployments can be rolled back.",
 *   "requestId": "b31f0c7e",
 *   "fieldErrors": { "port": "must be between 1 and 65535" }
 * }
 * </pre>
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String requestId,
        String path,
        Map<String, String> fieldErrors,
        List<String> details) {

    public static ApiErrorResponse of(
            int status, ErrorCode code, String message, String requestId, String path) {
        return new ApiErrorResponse(
                Instant.now(), status, code.name(), message, requestId, path, null, null);
    }

    public static ApiErrorResponse withFieldErrors(
            int status,
            ErrorCode code,
            String message,
            String requestId,
            String path,
            Map<String, String> fieldErrors) {
        return new ApiErrorResponse(
                Instant.now(), status, code.name(), message, requestId, path, fieldErrors, null);
    }
}
