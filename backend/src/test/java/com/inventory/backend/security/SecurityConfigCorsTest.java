package com.inventory.backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the CORS behaviour a deployed frontend depends on.
 *
 * <p>Regression context: the allowed-origin list was a hardcoded block of
 * localhost/RFC-1918 patterns, accompanied by a comment claiming production
 * would supply a real domain "via an environment variable" — no such variable
 * was ever read. A frontend deployed to {@code https://…vercel.app} therefore got
 * {@code 403 Forbidden} on its preflight with no
 * {@code Access-Control-Allow-Origin} header. The browser reports that as an
 * opaque network error, so a login attempt looked like a dead backend and the UI
 * told the user to run {@code docker compose up --build}.
 */
class SecurityConfigCorsTest {

    private CorsConfigurationSource corsConfigurationSource(String configuredOrigins) {
        SecurityConfig config = new SecurityConfig(null, null);
        ReflectionTestUtils.setField(config, "corsAllowedOrigins", configuredOrigins);
        return config.corsConfigurationSource();
    }

    private CorsConfiguration configurationFor(String configuredOrigins) {
        return corsConfigurationSource(configuredOrigins)
                .getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login"));
    }

    /**
     * Runs the preflight through Spring's real CORS processor and reports whether
     * the origin was accepted.
     *
     * <p>Asserts on the observable outcome — response status and the
     * {@code Access-Control-Allow-Origin} header — rather than on
     * {@code processRequest}'s return value, whose meaning is easy to invert.
     */
    private boolean preflightAccepted(String configuredOrigins, String requestOrigin) throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
        request.addHeader("Origin", requestOrigin);
        request.addHeader("Access-Control-Request-Method", "POST");

        MockHttpServletResponse response = new MockHttpServletResponse();
        CorsConfiguration configuration = corsConfigurationSource(configuredOrigins)
                .getCorsConfiguration(request);
        assertNotNull(configuration, "CORS configuration must cover every path");

        new DefaultCorsProcessor().processRequest(configuration, request, response);

        String allowOrigin = response.getHeader("Access-Control-Allow-Origin");
        if (allowOrigin == null) {
            assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus(),
                    "a rejected preflight must answer 403 without advertising an allowed origin");
            return false;
        }

        assertEquals(requestOrigin, allowOrigin);
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        return true;
    }

    @Test
    @DisplayName("a configured production origin is accepted")
    void configuredProductionOriginIsAccepted() throws IOException {
        assertTrue(preflightAccepted("https://app.example.com", "https://app.example.com"));
    }

    @Test
    @DisplayName("the deployed frontend origin used in production is accepted once configured")
    void deployedFrontendOriginIsAccepted() throws IOException {
        assertTrue(preflightAccepted(
                "https://inventory-system-blush-mu.vercel.app",
                "https://inventory-system-blush-mu.vercel.app"));
    }

    @Test
    @DisplayName("several origins can be supplied as a comma-separated list, with or without spaces")
    void multipleOriginsAreSupported() throws IOException {
        assertTrue(preflightAccepted(
                "https://a.example.com, https://b.example.com", "https://b.example.com"));
        assertTrue(preflightAccepted(
                "https://a.example.com,https://b.example.com", "https://b.example.com"));
        assertTrue(preflightAccepted(
                "https://a.example.com,https://b.example.com", "https://a.example.com"));
    }

    @Test
    @DisplayName("blank entries in the list are ignored rather than becoming patterns")
    void blankEntriesAreIgnored() throws IOException {
        assertTrue(preflightAccepted(" , https://app.example.com , ", "https://app.example.com"));
    }

    @Test
    @DisplayName("an origin that was not configured is refused")
    void unconfiguredOriginIsRefused() throws IOException {
        assertFalse(preflightAccepted("https://app.example.com", "https://evil.example.com"));
        assertFalse(preflightAccepted("", "https://app.example.com"));
    }

    @Test
    @DisplayName("configuring an exact origin must not implicitly allow other deployment hosts")
    void noImplicitWildcarding() {
        List<String> patterns = configurationFor("https://app.example.com").getAllowedOriginPatterns();

        assertTrue(patterns.contains("https://app.example.com"));
        assertFalse(patterns.contains("*"));
        assertFalse(patterns.stream().anyMatch(p -> p.contains("vercel.app")),
                "an unrelated deployment host must not be allowed: " + patterns);
    }

    @Test
    @DisplayName("localhost and LAN development origins keep working when nothing is configured")
    void developmentOriginsStillWorkByDefault() throws IOException {
        assertTrue(preflightAccepted("", "http://localhost:3005"));
        assertTrue(preflightAccepted("", "http://127.0.0.1:3000"));
        assertTrue(preflightAccepted("", "http://192.168.1.20:3001"));
    }

    @Test
    @DisplayName("credentials, methods and headers required by the API are all allowed")
    void credentialsAndMethodsAreConfigured() {
        CorsConfiguration configuration = configurationFor("https://app.example.com");

        assertTrue(configuration.getAllowCredentials(),
                "the API authenticates with a Bearer token sent as a request header, "
                        + "so Authorization must be an allowed header");
        assertTrue(configuration.getAllowedMethods()
                .containsAll(List.of("GET", "POST", "PUT", "PATCH", "DELETE")));
        assertTrue(configuration.getAllowedHeaders().contains("Authorization"));
        assertTrue(configuration.getAllowedHeaders().contains("Content-Type"));
        assertNull(configuration.getAllowedOrigins(),
                "allowedOrigins and allowedOriginPatterns must not both be set");
    }
}