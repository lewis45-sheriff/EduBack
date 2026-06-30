package com.EduePoa.EP.Multitenancy.service;

import com.EduePoa.EP.Multitenancy.config.TenantContext;
import com.EduePoa.EP.Multitenancy.entity.TenantConfiguration;
import com.EduePoa.EP.Multitenancy.repository.TenantConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves tenant context for M-Pesa callback endpoints that are unauthenticated.
 * <p>
 * Since M-Pesa callbacks arrive without JWT authentication, the tenant must be
 * resolved from the callback payload itself. This service maps the business shortcode
 * (or account reference) from the callback to the owning tenant via the
 * tenant_configurations table.
 * <p>
 * Config key convention: "mpesa.shortcode" stores the shortcode for each tenant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MpesaCallbackTenantResolver {

    private static final String MPESA_SHORTCODE_CONFIG_KEY = "mpesa.shortcode";

    private final TenantConfigurationRepository tenantConfigurationRepository;

    /**
     * Resolves and sets the tenant context from the M-Pesa business shortcode.
     * <p>
     * Performs a reverse lookup in tenant_configurations to find which tenant
     * has the given shortcode configured.
     *
     * @param businessShortCode the shortcode from the M-Pesa callback payload
     * @return true if tenant was resolved and context set, false otherwise
     */
    public boolean resolveAndSetTenantFromShortCode(String businessShortCode) {
        if (businessShortCode == null || businessShortCode.isBlank()) {
            log.warn("M-Pesa callback tenant resolution failed: businessShortCode is null or blank");
            return false;
        }

        Optional<TenantConfiguration> configOpt = tenantConfigurationRepository
                .findByConfigKeyAndConfigValue(MPESA_SHORTCODE_CONFIG_KEY, businessShortCode.trim());

        if (configOpt.isPresent()) {
            String tenantIdentifier = configOpt.get().getTenantIdentifier();
            TenantContext.setCurrentTenant(tenantIdentifier);
            log.info("M-Pesa callback tenant resolved from shortcode '{}': tenant='{}'",
                    businessShortCode, tenantIdentifier);
            return true;
        }

        log.warn("M-Pesa callback tenant resolution failed: no tenant configured for shortcode '{}'",
                businessShortCode);
        return false;
    }

    /**
     * Resolves and sets the tenant context from the M-Pesa account reference (BillRefNumber).
     * <p>
     * The account reference may contain a tenant identifier prefix (e.g., "bureti-high:12345")
     * or could be used as a secondary lookup mechanism.
     * <p>
     * This method attempts to extract a tenant identifier if the account reference
     * follows a "tenantId:studentId" pattern, then validates it exists in the configuration.
     *
     * @param accountReference the BillRefNumber/account reference from the callback payload
     * @return true if tenant was resolved and context set, false otherwise
     */
    public boolean resolveAndSetTenantFromAccountReference(String accountReference) {
        if (accountReference == null || accountReference.isBlank()) {
            log.warn("M-Pesa callback tenant resolution failed: accountReference is null or blank");
            return false;
        }

        // Check if account reference contains a tenant prefix (format: "tenantId:studentId")
        String trimmed = accountReference.trim();
        if (trimmed.contains(":")) {
            String potentialTenantId = trimmed.substring(0, trimmed.indexOf(":"));
            // Validate that this is actually a configured tenant by checking if it has any config
            if (tenantConfigurationRepository.existsByTenantIdentifierAndConfigKey(
                    potentialTenantId, MPESA_SHORTCODE_CONFIG_KEY)) {
                TenantContext.setCurrentTenant(potentialTenantId);
                log.info("M-Pesa callback tenant resolved from account reference prefix: tenant='{}'",
                        potentialTenantId);
                return true;
            }
        }

        log.debug("M-Pesa callback: could not resolve tenant from account reference '{}'", accountReference);
        return false;
    }

    /**
     * Resolves and sets the tenant context using a combined strategy:
     * first tries the business shortcode, then falls back to account reference.
     *
     * @param businessShortCode the shortcode from the callback payload
     * @param accountReference  the BillRefNumber/account reference from the callback payload
     * @return true if tenant was resolved and context set, false otherwise
     */
    public boolean resolveAndSetTenant(String businessShortCode, String accountReference) {
        // Primary strategy: resolve from shortcode
        if (resolveAndSetTenantFromShortCode(businessShortCode)) {
            return true;
        }

        // Fallback strategy: resolve from account reference
        if (resolveAndSetTenantFromAccountReference(accountReference)) {
            return true;
        }

        log.error("M-Pesa callback tenant resolution failed completely. " +
                        "ShortCode='{}', AccountReference='{}'. Transaction will not be processed.",
                businessShortCode, accountReference);
        return false;
    }
}
