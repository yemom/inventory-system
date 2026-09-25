package com.inventory.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
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
                        HttpSecurity http) throws Exception {

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
                                // JWT FILTER
                                // -------------------------------------------------
                                .addFilterBefore(
                                                jwtAuthFilter,
                                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
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

                // Allow configured frontends (local + docker-mapped ports)
                configuration.setAllowedOrigins(List.of(
                                "http://localhost:3000",
                                "http://localhost:3005",
                                "http://127.0.0.1:3000",
                                "http://127.0.0.1:3005"));
                configuration.setAllowedOriginPatterns(List.of(
                                "http://localhost:*",
                                "http://127.0.0.1:*"));

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