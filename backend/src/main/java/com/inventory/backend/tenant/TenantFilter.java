package com.inventory.backend.tenant;

import com.inventory.backend.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Resolves the current tenant from the authenticated user's JWT principal
 * and stores it in the TenantContext for the duration of the request.
 * <p>
 * The tenant ID is extracted from the CustomUserDetails principal.
 * If the user has an organization/tenant ID, it is used; otherwise
 * "default" is used (single-tenant mode).
 * <p>
 * This filter runs after the JWT authentication filter, so the
 * SecurityContext is already populated.
 */
@Slf4j
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                String tenantId = userDetails.getTenantId();
                if (tenantId != null && !tenantId.isBlank()) {
                    TenantContext.setCurrentTenantId(tenantId);
                } else {
                    TenantContext.setCurrentTenantId("default");
                }
            } else {
                TenantContext.setCurrentTenantId("default");
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
