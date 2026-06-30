package com.EduePoa.EP.Multitenancy.config;

/**
 * Thrown when a non-Platform_Admin user attempts to access a resource belonging to a different tenant.
 */
public class CrossTenantAccessException extends TenantException {

    public CrossTenantAccessException() {
        super("Access denied: resource belongs to a different tenant");
    }

    public CrossTenantAccessException(String message) {
        super(message);
    }

    public CrossTenantAccessException(String message, String tenantId) {
        super(message, tenantId);
    }
}
