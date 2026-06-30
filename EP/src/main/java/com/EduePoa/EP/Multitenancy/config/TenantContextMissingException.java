package com.EduePoa.EP.Multitenancy.config;

/**
 * Thrown when a tenant context is required but not established for the current request thread.
 */
public class TenantContextMissingException extends TenantException {

    public TenantContextMissingException() {
        super("Tenant context not established for this request");
    }

    public TenantContextMissingException(String message) {
        super(message);
    }
}
