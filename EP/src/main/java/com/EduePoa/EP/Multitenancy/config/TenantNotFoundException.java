package com.EduePoa.EP.Multitenancy.config;

/**
 * Thrown when a tenant identifier in a JWT or request does not correspond to any known tenant.
 */
public class TenantNotFoundException extends TenantException {

    public TenantNotFoundException(String tenantId) {
        super("Tenant not recognized", tenantId);
    }

    public TenantNotFoundException(String message, String tenantId) {
        super(message, tenantId);
    }
}
