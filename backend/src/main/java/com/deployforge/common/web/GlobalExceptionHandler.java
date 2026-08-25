package com.deployforge.common.web;

import com.deployforge.common.error.ApiErrorResponse;
import com.deployforge.common.error.ApiException;
import com.deployforge.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Centralised translation of exceptions into {@link ApiErrorResponse}.
 *
 * <p>Rules enforced here:
 *
 * <ul>
 *   <li>Deliberate {@link ApiException}s keep their code/status and are logged at WARN without a
 *       stack trace (they are expected outcomes, not incidents).
 *   <li>Anything unexpected is logged at ERROR <em>with</em> the stack trace, but the client only
 *       receives a generic message plus the request id.
 *   <li>No exception message from the persistence or servlet layer is ever echoed verbatim.
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(
            ApiException ex, HttpServletRequest request) {
        log.warn(
                "api_error code={} status={} path={} message={}",
                ex.getCode(),
                ex.getStatus().value(),
                request.getRequestURI(),
                ex.getMessage());
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBeanValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(
                        error ->
                                fieldErrors.putIfAbsent(
                                        error.getField(),
                                        error.getDefaultMessage() == null
                                                ? "is invalid"
                                                : error.getDefaultMessage()));
        ex.getBindingResult()
                .getGlobalErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getObjectName(), "is invalid"));

        return ResponseEntity.badRequest()
                .body(
                        ApiErrorResponse.withFieldErrors(
                                HttpStatus.BAD_REQUEST.value(),
                                ErrorCode.VALIDATION_FAILED,
                                "Request validation failed",
                                RequestIdFilter.currentRequestId(request),
                                request.getRequestURI(),
                                fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            fieldErrors.putIfAbsent(
                    String.valueOf(violation.getPropertyPath()), violation.getMessage());
        }
        return ResponseEntity.badRequest()
                .body(
                        ApiErrorResponse.withFieldErrors(
                                HttpStatus.BAD_REQUEST.value(),
                                ErrorCode.VALIDATION_FAILED,
                                "Request validation failed",
                                RequestIdFilter.currentRequestId(request),
                                request.getRequestURI(),
                                fieldErrors));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(
            Exception ex, HttpServletRequest request) {
        log.warn("malformed_request path={} type={}", request.getRequestURI(), ex.getClass().getSimpleName());
        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "The request could not be parsed",
                request);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleSpringAccessDenied(
            org.springframework.security.access.AccessDeniedException ex,
            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Access denied", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        // The raw SQL message can contain table/column internals - log it, never return it.
        log.warn("data_integrity_violation path={} message={}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(
                HttpStatus.CONFLICT,
                ErrorCode.CONFLICT,
                "The request conflicts with existing data",
                request);
    }

    @ExceptionHandler({NoHandlerFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ApiErrorResponse> handleNoHandler(
            Exception ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Endpoint not found", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("unhandled_exception path={}", request.getRequestURI(), ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Reference the request id when reporting this.",
                request);
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status, ErrorCode code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(
                        ApiErrorResponse.of(
                                status.value(),
                                code,
                                message,
                                RequestIdFilter.currentRequestId(request),
                                request.getRequestURI()));
    }
}
