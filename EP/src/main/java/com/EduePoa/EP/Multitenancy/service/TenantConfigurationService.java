package com.EduePoa.EP.Multitenancy.service;

import com.EduePoa.EP.Multitenancy.config.TenantConfigurationMissingException;
import com.EduePoa.EP.Multitenancy.config.TenantContext;
import com.EduePoa.EP.Multitenancy.entity.TenantConfiguration;
import com.EduePoa.EP.Multitenancy.repository.TenantConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing per-tenant configuration entries.
 * <p>
 * Supports encrypted (Base64-encoded) storage for sensitive values such as
 * M-Pesa credentials, API keys, and other secrets.
 * <p>
 * Note: Base64 encoding is used as a placeholder for development. Production
 * deployments should use proper encryption (e.g., AES-256) for sensitive values.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantConfigurationService {

    private final TenantConfigurationRepository configurationRepository;

    /**
     * Retrieves a configuration value for the given tenant and key.
     * Throws {@link TenantConfigurationMissingException} if the configuration entry does not exist.
     *
     * @param tenantId the tenant identifier
     * @param key      the configuration key (e.g., "mpesa.shortcode")
     * @return the configuration value (decrypted if sensitive)
     * @throws TenantConfigurationMissingException if the configuration is not found
     */
    @Transactional(readOnly = true)
    public String getConfig(String tenantId, String key) {
        TenantConfiguration config = configurationRepository
                .findByTenantIdentifierAndConfigKey(tenantId, key)
                .orElseThrow(() -> new TenantConfigurationMissingException(key, tenantId));

        return decodeIfSensitive(config);
    }

    /**
     * Retrieves a configuration value for the given tenant and key,
     * returning a default value if the entry does not exist.
     *
     * @param tenantId     the tenant identifier
     * @param key          the configuration key
     * @param defaultValue the value to return if configuration is not found
     * @return the configuration value (decrypted if sensitive), or defaultValue if not found
     */
    @Transactional(readOnly = true)
    public String getConfigOrDefault(String tenantId, String key, String defaultValue) {
        Optional<TenantConfiguration> config = configurationRepository
                .findByTenantIdentifierAndConfigKey(tenantId, key);

        return config.map(this::decodeIfSensitive).orElse(defaultValue);
    }

    /**
     * Retrieves a required configuration value for the current tenant (from TenantContext).
     * Throws {@link TenantConfigurationMissingException} if the configuration entry does not exist.
     *
     * @param key the configuration key
     * @return the configuration value (decrypted if sensitive)
     * @throws TenantConfigurationMissingException if the configuration is not found
     * @throws IllegalStateException               if TenantContext is not set
     */
    @Transactional(readOnly = true)
    public String getRequiredConfig(String key) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("TenantContext is not set. Cannot resolve required configuration.");
        }
        return getConfig(tenantId, key);
    }

    /**
     * Creates or updates a configuration entry for the specified tenant.
     * If the entry already exists, it is updated with the new value and sensitivity flag.
     * Sensitive values are encoded (Base64) before storage.
     *
     * @param tenantId    the tenant identifier
     * @param key         the configuration key
     * @param value       the configuration value (plain text)
     * @param isSensitive whether the value should be stored encoded
     */
    @Transactional
    public void setConfig(String tenantId, String key, String value, boolean isSensitive) {
        Optional<TenantConfiguration> existing = configurationRepository
                .findByTenantIdentifierAndConfigKey(tenantId, key);

        String storedValue = isSensitive ? encode(value) : value;

        if (existing.isPresent()) {
            TenantConfiguration config = existing.get();
            config.setConfigValue(storedValue);
            config.setIsSensitive(isSensitive);
            configurationRepository.save(config);
            log.info("Updated configuration '{}' for tenant '{}'", key, tenantId);
        } else {
            TenantConfiguration config = TenantConfiguration.builder()
                    .tenantIdentifier(tenantId)
                    .configKey(key)
                    .configValue(storedValue)
                    .isSensitive(isSensitive)
                    .build();
            configurationRepository.save(config);
            log.info("Created configuration '{}' for tenant '{}'", key, tenantId);
        }
    }

    /**
     * Retrieves all configuration entries for the specified tenant.
     * Sensitive values are returned decoded.
     *
     * @param tenantId the tenant identifier
     * @return list of all configuration entries for the tenant
     */
    @Transactional(readOnly = true)
    public List<TenantConfiguration> getAllConfigs(String tenantId) {
        List<TenantConfiguration> configs = configurationRepository.findByTenantIdentifier(tenantId);
        // Decode sensitive values in-place for the returned list
        configs.forEach(config -> {
            if (Boolean.TRUE.equals(config.getIsSensitive())) {
                config.setConfigValue(decode(config.getConfigValue()));
            }
        });
        return configs;
    }

    /**
     * Decodes the config value if it is marked as sensitive.
     */
    private String decodeIfSensitive(TenantConfiguration config) {
        if (Boolean.TRUE.equals(config.getIsSensitive())) {
            return decode(config.getConfigValue());
        }
        return config.getConfigValue();
    }

    /**
     * Encodes a value using Base64.
     * Note: In production, this should use AES or another proper encryption algorithm.
     */
    private String encode(String value) {
        if (value == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(value.getBytes());
    }

    /**
     * Decodes a Base64-encoded value.
     * Note: In production, this should use AES or another proper decryption algorithm.
     */
    private String decode(String encodedValue) {
        if (encodedValue == null) {
            return null;
        }
        return new String(Base64.getDecoder().decode(encodedValue));
    }
}
