package com.inventory.backend.tenant;

/**
 * Thread-local tenant context for multi-tenancy support.
 * <p>
 * The tenant ID is resolved from the JWT token by the TenantFilter
 * and stored in this context for the duration of the request.
 * All cache keys and database queries use this context to ensure
 * tenant data isolation.
 * <p>
 * If no tenant is set, "default" is used (single-tenant mode).
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private static final String DEFAULT_TENANT = "default";

    private TenantContext() {
    }

    public static void setCurrentTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getCurrentTenantId() {
        String tenantId = CURRENT_TENANT.get();
        return tenantId != null ? tenantId : DEFAULT_TENANT;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
