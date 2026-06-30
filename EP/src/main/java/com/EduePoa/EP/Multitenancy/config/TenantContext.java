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

    private TenantContext() {
        // Utility class — prevent instantiation
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
