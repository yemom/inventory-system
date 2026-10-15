package com.inventory.backend.exception;

import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.filter.RequestIdFilter;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
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

    /**
     * A request to a known path with the wrong verb.
     *
     * <p>Without this the catch-all {@code Exception} handler below would answer
     * 500. A {@code @RestControllerAdvice} takes precedence over Spring's
     * {@code DefaultHandlerExceptionResolver}, so without an explicit handler a
     * caller that POSTed to a GET endpoint — or an old client hitting a moved
     * route — was told the server had a fault rather than that its request was
     * malformed. That misdirects an operator towards the server when the problem
     * is in the caller.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "That operation is not supported on this endpoint.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handlePayloadTooLarge(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                "The uploaded file is too large.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        // IllegalArgumentException is thrown deliberately by our services with a
        // safe, user-facing message, so it is safe to return.
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    /**
     * A query parameter that cannot be read as the type the endpoint declares.
     *
     * <p>{@code ?from=not-a-date}, {@code ?id=abc}, {@code ?size=huge} — these
     * reach the dispatcher as a conversion failure and used to fall through to
     * the catch-all, which reports a server fault for what is a client mistake.
     * That is how {@code GET /api/v1/reports/sales?from=2026-07-01} answered 500
     * on a perfectly ordinary date: that endpoint alone wanted a timestamp.
     *
     * <p>The response names the offending parameter and nothing about its
     * internals — no expected type is echoed back with the conversion machinery's
     * message, which can quote class names and patterns.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        log.debug("Unreadable query parameter (requestId={}): {}",
                MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "'" + ex.getName() + "' is not a valid value for this request.");
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(EntityNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    /**
     * An unmapped URL is a 404, not a server fault.
     *
     * <p>A request for a path no controller claims is thrown by the dispatcher as
     * {@code NoResourceFoundException} and used to reach the catch-all handler,
     * which answers 500. That is wrong twice over: the caller is told the server
     * broke when it did not, and every client — including this repository's own
     * frontend, which uses a wrong path — has no way to tell a typo from an
     * outage. The message names the path the caller itself sent and nothing else.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(Exception ex) {
        String path = ex instanceof NoResourceFoundException resource
                ? resource.getResourcePath()
                : (ex instanceof NoHandlerFoundException handler ? handler.getRequestURL() : null);
        log.debug("No handler for path (requestId={}): {}",
                MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY), path);
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND",
                path == null || path.isBlank()
                        ? "That endpoint does not exist."
                        : "No endpoint matches /" + path + ".");
    }

    /**
 * Answers 409 for a business rule the caller has to resolve.
 *
 * <p>"Choose a warehouse", "there is no active warehouse", "not enough stock" —
 * these are ordinary outcomes of a real system, not faults. Reporting them as
 * 500 told a cashier the application had crashed at the moment they were trying
 * to take money, and put a red stack-trace page in front of the till.
 *
 * <p>The message is returned because it is written for the person holding the
 * till and says what to do next; it carries no internals.
 */
@ExceptionHandler(BusinessRuleViolationException.class)
public ResponseEntity<ApiResponse<Void>> handleBusinessRule(BusinessRuleViolationException ex) {
    log.info("Business rule refused the request ({}): {}", ex.getCode(), ex.getMessage());
    return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
}

@ExceptionHandler(DataIntegrityViolationException.class)
public ResponseEntity<ApiResponse<Void>> handleConflict(DataIntegrityViolationException ex) {
        // Never leak the SQL / constraint internals.
        log.warn("Data integrity violation: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "CONFLICT",
                "That operation conflicts with existing data. Please check for duplicates.");
    }

    /**
     * Answers 403 for anything refused.
     *
     * <p>When the refusing code supplied a message it is used. Those messages
     * describe the <em>caller's own</em> rights — "a MANAGER cannot assign the
     * MANAGER role" — rather than disclosing anything about other records, so
     * they are safe to return and are the difference between a user who
     * understands the refusal and one who thinks the app is broken.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(AccessDeniedException ex) {
        String message = ex.getMessage();
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN",
                message == null || message.isBlank()
                        ? "Access denied. You do not have permission to perform this action."
                        : message);
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
     * A customer session could not be opened, or its token did not
     * resolve.
     *
     * <p>401 rather than 403: the customer has not been refused
     * anything they hold; they have not proved who they are. The
     * message is fixed rather than taken from the exception so an
     * order number can never be probed for.
     */
    @ExceptionHandler(
            com.inventory.backend.chatbot.ChatbotService.InvalidCustomerCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCustomerCredentials(
            com.inventory.backend.chatbot.ChatbotService.InvalidCustomerCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CUSTOMER_CREDENTIALS",
                "No active account matches that email and order number.");
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