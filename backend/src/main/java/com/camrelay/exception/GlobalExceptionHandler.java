package com.camrelay.exception;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles application-specific exceptions in the Cam Relay application, returning responses in RFC 7807 Problem Details format.
 * @since 1.0
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {
    /**
     * Handles {@link AuthenticationException} for authentication-related errors, including {@link UserAlreadyExistsException}.
     * @param ex the authentication exception
     * @param request the HTTP request
     * @return a ResponseEntity containing problem details with HTTP status 401 or 409
     * @since 1.0
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        log.error("Authentication error: {}", ex.getMessage(), ex);
        HttpStatus status = ex instanceof UserAlreadyExistsException ? HttpStatus.CONFLICT : HttpStatus.UNAUTHORIZED;
        String title = ex instanceof UserAlreadyExistsException ? "Registration Failed" : "Authentication Failed";
        String type = ex instanceof UserAlreadyExistsException ? "/problems/registration-failed" : "/problems/authentication-failed";
        return buildProblemDetailsResponse(
                status,
                title,
                ex.getMessage(),
                type,
                request.getRequestURI()
        );
    }

    /**
     * Handles {@link AccessDeniedException} for unauthorized access attempts.
     * @param ex the custom access denied exception
     * @param request the HTTP request
     * @return a ResponseEntity containing problem details with HTTP status 403
     * @since 1.0
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleCustomAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        log.error("Access denied: {}", ex.getMessage(), ex);
        return buildProblemDetailsResponse(
                HttpStatus.FORBIDDEN,
                "Access Denied",
                ex.getMessage(),
                "/problems/access-denied",
                request.getRequestURI()
        );
    }

    /**
     * Handles {@link org.springframework.security.access.AccessDeniedException} for unauthorized access attempts.
     * @param ex the Spring Security access denied exception
     * @param request the HTTP request
     * @return a ResponseEntity containing problem details with HTTP status 403
     * @since 1.0
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        log.error("Access denied: {}", ex.getMessage(), ex);
        return buildProblemDetailsResponse(
                HttpStatus.FORBIDDEN,
                "Access Denied",
                "You do not have permission to access this resource",
                "/problems/access-denied",
                request.getRequestURI()
        );
    }

    /**
     * Handles {@link UserNotFoundException} for cases where a user is not found.
     * @param ex the user not found exception
     * @param request the HTTP request
     * @return a ResponseEntity containing problem details with HTTP status 404
     * @since 1.0
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFoundException(UserNotFoundException ex, HttpServletRequest request) {
        log.error("User not found: {}", ex.getMessage(), ex);
        return buildProblemDetailsResponse(
                HttpStatus.NOT_FOUND,
                "User Not Found",
                ex.getMessage(),
                "/problems/user-not-found",
                request.getRequestURI()
        );
    }

    /**
     * Handles {@link ResponseStatusException} for HTTP status errors.
     * @param ex the response status exception
     * @param request the HTTP request
     * @return a ResponseEntity containing problem details with the corresponding HTTP status
     * @since 1.0
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex, HttpServletRequest request) {
        log.error("Response status error: {}", ex.getReason(), ex);
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        String type;
        String title;

        if (status == HttpStatus.UNAUTHORIZED) {
            type = "/problems/authentication-failed";
            title = "Authentication Failed";
        } else if (status == HttpStatus.FORBIDDEN) {
            type = "/problems/access-denied";
            title = "Access Denied";
        } else if (status == HttpStatus.BAD_REQUEST) {
            type = "/problems/validation-error";
            title = "Validation Error";
        } else {
            type = "/problems/response-status-error";
            title = status.getReasonPhrase();
        }

        return buildProblemDetailsResponse(
                status,
                title,
                ex.getReason() != null ? ex.getReason() : "Internal server error",
                type,
                request.getRequestURI()
        );
    }

    /**
     * Handles unexpected exceptions not caught by specific handlers.
     * @param ex the generic exception
     * @param request the HTTP request
     * @return a ResponseEntity containing problem details with HTTP status 500
     * @since 1.0
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildProblemDetailsResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "Unexpected error: " + ex.getMessage(),
                "/problems/internal-server-error",
                request.getRequestURI()
        );
    }

    /**
     * Handles {@link RequestNotPermitted} for rate limit exceeded errors from Resilience4j.
     * @param ex the rate limit exceeded exception
     * @param request the HTTP request
     * @return a ResponseEntity containing problem details with HTTP status 429
     * @since 1.0
     */
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitExceeded(RequestNotPermitted ex, HttpServletRequest request) {
        log.error("Rate limit exceeded: {}", ex.getMessage(), ex);
        return buildProblemDetailsResponse(
                HttpStatus.TOO_MANY_REQUESTS,
                "Rate Limit Exceeded",
                "Too many requests - rate limit exceeded for " + ex.getMessage(),
                "/problems/rate-limit-exceeded",
                request.getRequestURI()
        );
    }

    /**
     * Handles {@link MethodArgumentNotValidException} which occurs when
     * validation of a request body annotated with {@code @Valid} fails.
     * @param ex the exception containing field errors
     * @param request the HTTP request
     * @return a {@link ResponseEntity} containing problem details with HTTP status 400
     * @since 1.0
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String errorMessage = ex.getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.error("Validation error: {}", errorMessage, ex);
        return buildProblemDetailsResponse(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                errorMessage.isEmpty() ? "Validation failed" : errorMessage,
                "/problems/validation-error",
                request.getRequestURI()
        );
    }

    /**
     * Handles {@link ConstraintViolationException} which occurs when
     * validation of method parameters, path variables, or request parameters fails.
     * @param ex the exception containing constraint violations
     * @param request the HTTP request
     * @return a {@link ResponseEntity} containing problem details with HTTP status 400
     * @since 1.0
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        String errorMessage = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining("; "));
        log.error("Constraint violation error: {}", errorMessage, ex);
        return buildProblemDetailsResponse(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                errorMessage.isEmpty() ? "Validation failed" : errorMessage,
                "/problems/validation-error",
                request.getRequestURI()
        );
    }

    /**
     * Handles {@link BadCredentialsException} for invalid authentication credentials.
     * @param ex the bad credentials exception
     * @param request the HTTP request
     * @return a ResponseEntity containing problem details with HTTP status 401
     * @since 1.0
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentialsException(BadCredentialsException ex, HttpServletRequest request) {
        log.error("Authentication error: {}", ex.getMessage(), ex);
        return buildProblemDetailsResponse(
                HttpStatus.UNAUTHORIZED,
                "Authentication Failed",
                "Invalid username or password",
                "/problems/authentication-failed",
                request.getRequestURI()
        );
    }

    /**
     * Handles {@link HttpMessageNotReadableException} for malformed JSON or unrecognized fields.
     * @param ex the HTTP message not readable exception
     * @param request the HTTP request
     * @return a ResponseEntity containing problem details with HTTP status 400
     * @since 1.0
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        if (ex.getCause() instanceof UnrecognizedPropertyException unrecognized) {
            String fieldName = unrecognized.getPropertyName();
            log.error("Unknown field in request: {}", fieldName, ex);
            return buildProblemDetailsResponse(
                    HttpStatus.BAD_REQUEST,
                    "Invalid Request",
                    "Unknown field: " + fieldName,
                    "/problems/invalid-request",
                    request.getRequestURI()
            );
        }

        log.error("Malformed JSON request", ex);
        return buildProblemDetailsResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid Request",
                "Malformed JSON request",
                "/problems/malformed-json",
                request.getRequestURI()
        );
    }

    /**
     * Builds a problem details response in RFC 7807 format.
     * @param status the HTTP status code
     * @param title the title of the error
     * @param detail the detailed error message
     * @param type the URI identifying the error type
     * @param instance the URI of the request causing the error
     * @return a ResponseEntity containing the problem details
     * @since 1.0
     */
    private ResponseEntity<Map<String, Object>> buildProblemDetailsResponse(HttpStatus status, String title, String detail, String type, String instance) {
        Map<String, Object> problemDetails = new HashMap<>();
        problemDetails.put("type", type);
        problemDetails.put("title", title);
        problemDetails.put("status", status.value());
        problemDetails.put("detail", detail);
        problemDetails.put("instance", instance);
        return new ResponseEntity<>(problemDetails, status);
    }
}
