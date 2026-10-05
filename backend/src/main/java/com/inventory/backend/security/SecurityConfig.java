package com.inventory.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.backend.ratelimit.RateLimitFilter;
import com.inventory.backend.tenant.TenantFilter;
import lombok.RequiredArgsConstructor;

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

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthFilter;
        private final CustomUserDetailsService userDetailsService;

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

                                                // Public health check (no sensitive data)
                                                .requestMatchers("/api/v1/dashboard/health", "/actuator/health")
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

                // Public origins (explicit allow-list). Production is expected
                // to set a real domain here via an environment variable; the
                // patterns below cover local + LAN development.
                configuration.setAllowedOriginPatterns(List.of(
                                "http://localhost:*",
                                "http://127.0.0.1:*",
                                // Allow the dev machine's LAN IP on any port so
                                // the app opened as http://192.168.x.x:3001 (or
                                // 10.x.x.x / 172.16-31.x.x) is not CORS-blocked.
                                // Production deployments are expected to serve
                                // the frontend and API from the same origin or to
                                // pin this via environment, so these private-
                                // network patterns do not expand the real attack
                                // surface.
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
                                "http://*.localtest.me:*"));

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