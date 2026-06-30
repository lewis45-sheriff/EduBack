package com.EduePoa.EP.Multitenancy.config;

/**
 * Thrown when a tenant registration request contains a Tenant_Identifier that already exists.
 */
public class TenantConflictException extends TenantException {

    public TenantConflictException(String tenantIdentifier) {
        super("Tenant identifier already exists: " + tenantIdentifier, tenantIdentifier);
    }
}
