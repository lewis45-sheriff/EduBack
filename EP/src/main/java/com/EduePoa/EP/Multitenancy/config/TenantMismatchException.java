package com.EduePoa.EP.Multitenancy.config;

/**
 * Thrown when an operation violates tenant isolation — e.g., attempting to persist or update
 * an entity whose tenant_id does not match the current tenant context.
 */
public class TenantMismatchException extends TenantException {

    public TenantMismatchException(String contextTenant, String entityTenant) {
        super("Operation violates tenant isolation", contextTenant);
    }

    public TenantMismatchException(String message) {
        super(message);
    }
}
