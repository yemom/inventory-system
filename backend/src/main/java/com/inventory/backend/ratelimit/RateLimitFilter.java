package com.inventory.backend.ratelimit;

import com.inventory.backend.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rate limiting filter that applies different limits based on endpoint category.
 * <p>
 * Categories:
 * - LOGIN: POST /api/v1/auth/login — strict per-IP limit
 * - SIGNUP: POST /api/v1/auth/register — strict per-IP limit
 * - REPORTS: /api/v1/reports/** — per-user limit
 * - API: all other authenticated endpoints — per-user limit
 * <p>
 * Rate limit state is stored in Redis, so multiple API instances share
 * the same counters (distributed rate limiting).
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Never rate-limit CORS preflight (OPTIONS) requests. They are part of
        // the same logical request as the real call and must always pass so the
        // browser can read the Access-Control-Allow-* headers.
        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Determine rate limit type based on endpoint
        RateLimiterService.RateLimitType type = resolveRateLimitType(path, method);

        if (type == null) {
            // No rate limiting for this endpoint
            filterChain.doFilter(request, response);
            return;
        }

        // Build rate limit key
        String key = buildKey(type, request);

        RateLimiterService.RateLimitResult result = rateLimiterService.isAllowed(key, type);

        // Add rate limit headers (standard RateLimit header draft)
        long capacity = rateLimiterService.getCapacity(type);
        response.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemainingTokens()));

        if (!result.isAllowed()) {
            log.warn("Rate limit exceeded for key={}, type={}, path={}", key, type, path);
            response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ApiResponse<Void> body = ApiResponse.error(
                    "RATE_LIMIT_EXCEEDED",
                    "Too many requests. Please try again in " + result.getRetryAfterSeconds() + " seconds."
            );
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RateLimiterService.RateLimitType resolveRateLimitType(String path, String method) {
        if ("POST".equals(method)) {
            if (path.endsWith("/api/v1/auth/login")) {
                return RateLimiterService.RateLimitType.LOGIN;
            }
            if (path.endsWith("/api/v1/auth/register")) {
                return RateLimiterService.RateLimitType.SIGNUP;
            }
        }

        if (path.startsWith("/api/v1/reports/")) {
            return RateLimiterService.RateLimitType.REPORTS;
        }

        // All other authenticated API endpoints
        if (path.startsWith("/api/v1/")) {
            return RateLimiterService.RateLimitType.API;
        }

        return null;
    }

    private String buildKey(RateLimiterService.RateLimitType type, HttpServletRequest request) {
        String ip = getClientIp(request);

        return switch (type) {
            case LOGIN, SIGNUP -> "rate:" + type.name().toLowerCase() + ":ip:" + ip;
            case API, REPORTS -> {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String username) {
                    yield "rate:" + type.name().toLowerCase() + ":user:" + username;
                }
                // Fall back to IP if not authenticated
                yield "rate:" + type.name().toLowerCase() + ":ip:" + ip;
            }
        };
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
