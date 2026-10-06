package com.inventory.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.backend.ratelimit.RateLimitFilter;
import com.inventory.backend.tenant.TenantFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import org.springframework.security.web.AuthenticationEntryPoint;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthFilter;
        private final CustomUserDetailsService userDetailsService;

        /**
         * Extra browser origins allowed to call the API, comma-separated.
         * Bound from {@code app.cors.allowed-origins} (env
         * {@code CORS_ALLOWED_ORIGINS}). Empty by default: without it only the
         * local-development origins below are accepted.
         */
        @Value("${app.cors.allowed-origins:}")
        private String corsAllowedOrigins;

        /**
         * Origins needed for development only — the Next.js dev server on
         * localhost (any port), and a LAN IP so the app opened as
         * {@code http://192.168.x.x:3001} is not CORS-blocked.
         */
        private static final List<String> LOCAL_DEVELOPMENT_ORIGIN_PATTERNS = List.of(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "http://192.168.*:*",
                        "http://10.*:*",
                        "http://172.16.*:*", "http://172.17.*:*", "http://172.18.*:*",
                        "http://172.19.*:*", "http://172.20.*:*", "http://172.21.*:*",
                        "http://172.22.*:*", "http://172.23.*:*", "http://172.24.*:*",
                        "http://172.25.*:*", "http://172.26.*:*", "http://172.27.*:*",
                        "http://172.28.*:*", "http://172.29.*:*", "http://172.30.*:*",
                        "http://172.31.*:*",
                        "http://*.localhost:*",
                        "http://localtest.me:*",
                        "http://*.localtest.me:*");

        // =========================================================
        // SECURITY FILTER CHAIN
        // =========================================================

        @Bean
        public SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        RateLimitFilter rateLimitFilter,
                        TenantFilter tenantFilter) throws Exception {

                http

                                // -------------------------------------------------
                                // CORS
                                // -------------------------------------------------
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                                // -------------------------------------------------
                                // CSRF
                                // -------------------------------------------------
                                .csrf(AbstractHttpConfigurer::disable)

                                // -------------------------------------------------
                                // AUTH ENTRY POINT
                                // Without this, Spring Security's default
                                // Http403ForbiddenEntryPoint returns 403 for
                                // missing/invalid/expired JWTs instead of 401,
                                // which breaks the frontend's 401-based
                                // auto-logout/redirect-to-login logic.
                                // -------------------------------------------------
                                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))

                                // -------------------------------------------------
                                // AUTHORIZATION
                                // -------------------------------------------------
                                .authorizeHttpRequests(auth -> auth

                                                // IMPORTANT:
                                                // Allow browser CORS preflight requests
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                                                // Login, register, refresh token, etc.
                                                .requestMatchers("/api/v1/auth/**").permitAll()

                                                // Public health check (no sensitive data; show-details=never).
                                                // "/actuator/health/**" rather than the
                                                // exact path: the liveness/readiness
                                                // probe groups are sub-paths, and they
                                                // were returning 401 because only
                                                // "/actuator/health" was permitted.
                                                .requestMatchers("/api/v1/dashboard/health", "/actuator/health", "/actuator/health/**")
                                                .permitAll()

                                                // All other API endpoints require JWT
                                                .anyRequest().authenticated())

                                // -------------------------------------------------
                                // SESSION
                                // -------------------------------------------------
                                .sessionManagement(session -> session.sessionCreationPolicy(
                                                SessionCreationPolicy.STATELESS))

                                // -------------------------------------------------
                                // AUTHENTICATION PROVIDER
                                // -------------------------------------------------
                                .authenticationProvider(authenticationProvider())

                                // -------------------------------------------------
                                // RATE LIMIT FILTER (before JWT auth)
                                // -------------------------------------------------
                                .addFilterBefore(
                                                rateLimitFilter,
                                                UsernamePasswordAuthenticationFilter.class)

                                // -------------------------------------------------
                                // TENANT FILTER (resolves tenant from JWT)
                                // -------------------------------------------------
                                .addFilterBefore(
                                                tenantFilter,
                                                UsernamePasswordAuthenticationFilter.class)

                                // -------------------------------------------------
                                // JWT FILTER
                                // -------------------------------------------------
                                .addFilterBefore(
                                                jwtAuthFilter,
                                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        // =========================================================
        // RATE LIMIT + TENANT FILTERS (defined as beans here so
        // they are only created with the full security config —
        // @WebMvcTest slice tests exclude this @Configuration and
        // therefore never instantiate them)
        // =========================================================

        @Bean
        public RateLimitFilter rateLimitFilter(
                        com.inventory.backend.ratelimit.RateLimiterService rateLimiterService,
                        ObjectMapper objectMapper) {
                return new RateLimitFilter(rateLimiterService, objectMapper);
        }

        @Bean
        public TenantFilter tenantFilter() {
                return new TenantFilter();
        }

        // =========================================================
        // AUTHENTICATION PROVIDER
        // =========================================================

        @Bean
        public AuthenticationProvider authenticationProvider() {

                DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();

                authProvider.setUserDetailsService(userDetailsService);

                authProvider.setPasswordEncoder(passwordEncoder());

                return authProvider;
        }

        // =========================================================
        // AUTHENTICATION MANAGER
        // =========================================================

        @Bean
        public AuthenticationManager authenticationManager(
                        AuthenticationConfiguration config) throws Exception {

                return config.getAuthenticationManager();
        }

        // =========================================================
        // PASSWORD ENCODER
        // =========================================================

        @Bean
        public PasswordEncoder passwordEncoder() {

                return new BCryptPasswordEncoder();
        }

        // =========================================================
        // 401 ENTRY POINT (replaces default Http403ForbiddenEntryPoint)
        // =========================================================

        @Bean
        public AuthenticationEntryPoint unauthorizedEntryPoint() {
                return (request, response, authException) -> {
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setStatus(401);
                        new ObjectMapper().writeValue(
                                        response.getOutputStream(),
                                        java.util.Map.of(
                                                        "success", false,
                                                        "message", "Authentication required. Please log in."));
                };
        }

        // =========================================================
        // CORS CONFIGURATION
        // =========================================================

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {

                CorsConfiguration configuration = new CorsConfiguration();

                // Production origins are NOT hardcoded. They come from
                // `app.cors.allowed-origins` (env: CORS_ALLOWED_ORIGINS), a
                // comma-separated list of origins or wildcard patterns.
                //
                // This used to be a fixed list of localhost/RFC-1918 patterns
                // with a comment claiming production would "set a real domain
                // here via an environment variable" — but nothing ever read
                // such a variable. The deployed frontend therefore got a 403 on
                // its CORS preflight with no Access-Control-Allow-Origin header,
                // which the browser surfaces as an opaque network error: the
                // login request looked like the backend was down, and the UI
                // told the user to run `docker compose up --build`.
                //
                // The localhost/LAN patterns below are kept because they are
                // needed for local and on-LAN development. Production origins
                // must be listed explicitly: a wildcard such as
                // https://*.vercel.app would let *any* Vercel deployment make
                // credentialed (allowCredentials=true) calls to this API.
                List<String> originPatterns = new ArrayList<>(LOCAL_DEVELOPMENT_ORIGIN_PATTERNS);
                if (StringUtils.hasText(corsAllowedOrigins)) {
                        for (String origin : StringUtils.commaDelimitedListToStringArray(corsAllowedOrigins)) {
                                String trimmed = origin.trim();
                                if (!trimmed.isEmpty()) {
                                        originPatterns.add(trimmed);
                                }
                        }
                } else {
                        log.warn("CORS_ALLOWED_ORIGINS is not set. Only local-development origins are "
                                + "accepted, so a browser on any deployed frontend origin will block every "
                                + "API call. Set it to the comma-separated origin(s) the frontend is served "
                                + "from.");
                }

                configuration.setAllowedOriginPatterns(originPatterns);

                // HTTP methods
                configuration.setAllowedMethods(List.of(
                                "GET",
                                "POST",
                                "PUT",
                                "PATCH",
                                "DELETE",
                                "OPTIONS"));

                // Request headers
                configuration.setAllowedHeaders(List.of(
                                "Authorization",
                                "Content-Type",
                                "Accept",
                                "Origin"));

                // Response headers accessible to frontend
                configuration.setExposedHeaders(List.of(
                                "Authorization"));

                // Cookies / credentials
                configuration.setAllowCredentials(true);

                // Apply CORS to every endpoint
                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

                source.registerCorsConfiguration(
                                "/**",
                                configuration);

                return source;
        }
}