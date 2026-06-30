package com.EduePoa.EP.Multitenancy.config;

/**
 * Thrown when a required tenant-specific configuration entry (e.g., M-Pesa credentials,
 * SMS gateway settings) is not found for the current tenant.
 */
public class TenantConfigurationMissingException extends TenantException {

    public TenantConfigurationMissingException(String configKey, String tenantId) {
        super("Required configuration '" + configKey + "' not found for tenant '" + tenantId + "'", tenantId);
    }

    public TenantConfigurationMissingException(String message) {
        super(message);
    }
}
