package com.inventory.backend.controller;

import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.filter.RequestIdFilter;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

import java.time.Instant;
import java.util.Map;

/**
 * Replaces Spring Boot's default Whitelabel HTML error page with a consistent
 * JSON error body for every unhandled error (including errors raised outside the
 * controller layer, which {@code GlobalExceptionHandler} cannot catch).
 * <p>
 * Example body:
 * <pre>
 * {
 *   "success": false,
 *   "code": "INTERNAL_SERVER_ERROR",
 *   "message": "An unexpected error occurred.",
 *   "requestId": "3f1c...",
 *   "timestamp": "2026-10-04T14:45:00Z"
 * }
 * </pre>
 * Stack traces, SQL text and internal details are never returned to the client.
 */
@RestController
public class ApiErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;

    public ApiErrorController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @RequestMapping(value = "${server.error.path:${error.path:/error}}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleError(HttpServletRequest request) {

        HttpStatus status = resolveStatus(request);

        Map<String, Object> attributes = errorAttributes.getErrorAttributes(
                new ServletWebRequest(request), ErrorAttributeOptions.defaults());

        String code = switch (status.value()) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            case 429 -> "RATE_LIMIT_EXCEEDED";
            default -> status.is5xxServerError() ? "INTERNAL_SERVER_ERROR" : "REQUEST_FAILED";
        };

        // For 5xx we never echo the underlying message (it can contain SQL or
        // infrastructure details); a generic message is returned instead.
        String message = status.is5xxServerError()
                ? "An unexpected error occurred. Please try again."
                : String.valueOf(attributes.getOrDefault("message", status.getReasonPhrase()));

        ApiResponse<Map<String, Object>> body = ApiResponse.<Map<String, Object>>error(code, message)
                .toBuilder()
                .requestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY))
                .data(Map.of("timestamp", Instant.now().toString()))
                .build();

        return ResponseEntity.status(status).body(body);
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object statusAttr = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusAttr instanceof Integer statusCode) {
            try {
                return HttpStatus.valueOf(statusCode);
            } catch (IllegalArgumentException ignored) {
                return HttpStatus.INTERNAL_SERVER_ERROR;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}