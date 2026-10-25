package com.EduePoa.EP.Multitenancy.config;

/**
 * Thread-local holder for the current tenant identifier.
 * <p>
 * Stores the tenant context for the duration of a single request thread,
 * ensuring all operations within that thread are scoped to the correct tenant.
 * Must be cleared after each request to prevent tenant leakage between requests.
 */
public final class TenantContext {

    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();

    /**
     * Marks the current thread as operating in cross-tenant (platform) mode. Set for a
     * Platform_Admin that has NOT pinned a specific tenant via the {@code X-Tenant-ID} header.
     * In this mode the Hibernate tenant filter is bypassed (reads span all tenants) and the
     * write-side single-tenant mismatch guard is relaxed, since the platform admin legitimately
     * creates/updates entities belonging to other tenants (e.g. managing the tenants catalogue).
     */
    private static final ThreadLocal<Boolean> crossTenant = new ThreadLocal<>();

    private TenantContext() {
        // Utility class — prevent instantiation
    }

    /** Enables or disables cross-tenant (platform) mode for this thread. */
    public static void setCrossTenant(boolean enabled) {
        crossTenant.set(enabled);
    }

    /** Whether this thread is operating in cross-tenant (platform) mode. */
    public static boolean isCrossTenant() {
        return Boolean.TRUE.equals(crossTenant.get());
    }

    /**
     * Sets the current tenant identifier for this thread.
     *
     * @param tenantId the unique tenant identifier (e.g., "bureti-high")
     */
    public static void setCurrentTenant(String tenantId) {
        currentTenant.set(tenantId);
    }

    /**
     * Returns the current tenant identifier for this thread.
     *
     * @return the tenant identifier, or {@code null} if not set
     */
    public static String getCurrentTenant() {
        return currentTenant.get();
    }

    /**
     * Clears the tenant context for this thread.
     * Must be called after request processing to prevent tenant leakage.
     */
    public static void clear() {
        currentTenant.remove();
        crossTenant.remove();
    }

    /**
     * Checks whether a tenant context is currently set for this thread.
     *
     * @return {@code true} if a tenant identifier is set, {@code false} otherwise
     */
    public static boolean isSet() {
        return currentTenant.get() != null;
    }
}
