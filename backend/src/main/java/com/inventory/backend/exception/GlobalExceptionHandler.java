package com.inventory.backend.exception;

import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.filter.RequestIdFilter;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Global exception handler.
 * <p>
 * Security rule: <b>never</b> return {@code ex.getMessage()} for unexpected or
 * infrastructure failures. Messages such as JDBC/SQL text, table names or
 * connection details are logged server-side (with the requestId) and replaced
 * with a generic message for the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String message) {
        ApiResponse<Void> body = ApiResponse.<Void>error(code, message).toBuilder()
                .requestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY))
                .build();
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", errors);
    }

    /**
     * Malformed/unparseable request body (bad JSON, wrong content type, missing
     * quotes, etc.). Must be a 400, not a 500 — but it falls into the generic
     * handler if not mapped.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body (requestId={}): {}",
                MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST_BODY",
                "The request body is missing or contains invalid JSON.");
    }

    /**
     * Tokens that fail validation/signature/expiration should produce a 401,
     * not a 500.
     */
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiResponse<Void>> handleJwt(JwtException ex) {
        log.debug("JWT validation failure (requestId={}): {}",
                MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                "The provided token is invalid or expired.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        // IllegalArgumentException is thrown deliberately by our services with a
        // safe, user-facing message, so it is safe to return.
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(EntityNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(DataIntegrityViolationException ex) {
        // Never leak the SQL / constraint internals.
        log.warn("Data integrity violation: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "CONFLICT",
                "That operation conflicts with existing data. Please check for duplicates.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "Access denied. You do not have permission to perform this action.");
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabled(DisabledException ex) {
        return build(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED",
                "Account is deactivated. Please contact an administrator.");
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLocked(LockedException ex) {
        return build(HttpStatus.UNAUTHORIZED, "ACCOUNT_LOCKED",
                "Account is locked. Please contact an administrator.");
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuth(Exception ex) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password.");
    }

    /**
     * Any other authentication failure. The original message may contain JDBC
     * text (e.g. "relation \"users\" does not exist"), so it is logged but never
     * returned to the client.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.error("Authentication failure (requestId={}): {}",
                MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY), ex.getMessage(), ex);
        return build(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED",
                "Authentication failed. Please check your details and try again.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unhandled exception (requestId={})", MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please try again.");
    }
}