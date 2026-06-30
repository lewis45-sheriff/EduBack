package com.EduePoa.EP.Multitenancy.repository;

import com.EduePoa.EP.Multitenancy.entity.TenantConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantConfigurationRepository extends JpaRepository<TenantConfiguration, Long> {

    Optional<TenantConfiguration> findByTenantIdentifierAndConfigKey(String tenantIdentifier, String configKey);

    List<TenantConfiguration> findByTenantIdentifier(String tenantIdentifier);

    List<TenantConfiguration> findByTenantIdentifierAndIsSensitive(String tenantIdentifier, Boolean isSensitive);

    boolean existsByTenantIdentifierAndConfigKey(String tenantIdentifier, String configKey);

    void deleteByTenantIdentifierAndConfigKey(String tenantIdentifier, String configKey);

    /**
     * Reverse lookup: find a tenant configuration entry by config key and config value.
     * Used to resolve which tenant owns a specific M-Pesa shortcode.
     *
     * @param configKey   the configuration key (e.g., "mpesa.shortcode")
     * @param configValue the configuration value (e.g., "174379")
     * @return the matching TenantConfiguration, if found
     */
    Optional<TenantConfiguration> findByConfigKeyAndConfigValue(String configKey, String configValue);
}
