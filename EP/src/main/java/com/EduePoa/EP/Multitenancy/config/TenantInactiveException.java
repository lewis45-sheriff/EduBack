package com.EduePoa.EP.Multitenancy.config;

/**
 * Thrown when a request targets a tenant that is suspended or decommissioned.
 */
public class TenantInactiveException extends TenantException {

    public TenantInactiveException(String tenantId, String status) {
        super("Tenant account is " + status.toLowerCase(), tenantId);
    }

    public TenantInactiveException(String message) {
        super(message);
    }
}
