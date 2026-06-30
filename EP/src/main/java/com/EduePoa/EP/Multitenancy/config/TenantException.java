package com.EduePoa.EP.Multitenancy.config;

/**
 * Base exception for all tenant-related errors in the multi-tenancy system.
 */
public class TenantException extends RuntimeException {

    private final String tenantId;

    public TenantException(String message) {
        super(message);
        this.tenantId = TenantContext.getCurrentTenant();
    }

    public TenantException(String message, String tenantId) {
        super(message);
        this.tenantId = tenantId;
    }

    public TenantException(String message, Throwable cause) {
        super(message, cause);
        this.tenantId = TenantContext.getCurrentTenant();
    }

    public String getTenantId() {
        return tenantId;
    }
}
