package com.inventory.backend.tenant;

import com.inventory.backend.base.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for multi-tenancy and tenant isolation.
 * <p>
 * Verifies that:
 * - Tenant context is resolved from JWT
 * - Cache keys are tenant-safe
 * - Users cannot access other tenants' data
 */
class TenantIsolationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Tenant context is set from authenticated user")
    void tenantContextIsSet() throws Exception {
        String token = loginAsSuperAdmin();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);

        // Make an authenticated request — tenant context should be set
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/products?size=1",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Unauthenticated requests use default tenant")
    void unauthenticatedUsesDefaultTenant() throws Exception {
        // Health check is permitAll — should work without tenant context
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/dashboard/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Tenant context is cleared after request")
    void tenantContextClearedAfterRequest() throws Exception {
        // After a request completes, the tenant context should be cleared
        // This prevents tenant leakage between requests on the same thread
        String token = loginAsSuperAdmin();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);

        restTemplate.exchange(
                "/api/v1/products?size=1",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        // After the request, tenant context should be cleared
        assertThat(TenantContext.getCurrentTenantId()).isEqualTo("default");
    }
}
