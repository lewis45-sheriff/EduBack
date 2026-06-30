package com.EduePoa.EP.Multitenancy.service;

import com.EduePoa.EP.Multitenancy.config.TenantConfigurationMissingException;
import com.EduePoa.EP.Multitenancy.config.TenantContext;
import com.EduePoa.EP.Multitenancy.entity.TenantConfiguration;
import com.EduePoa.EP.Multitenancy.repository.TenantConfigurationRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantConfigurationServiceTest {

    @Mock
    private TenantConfigurationRepository configurationRepository;

    @InjectMocks
    private TenantConfigurationService configurationService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("getConfig()")
    class GetConfigTests {

        @Test
        @DisplayName("should return plain value for non-sensitive config")
        void getConfig_nonSensitive_returnsPlainValue() {
            TenantConfiguration config = TenantConfiguration.builder()
                    .tenantIdentifier("bureti-high")
                    .configKey("sms.sender_id")
                    .configValue("BURETI")
                    .isSensitive(false)
                    .build();

            when(configurationRepository.findByTenantIdentifierAndConfigKey("bureti-high", "sms.sender_id"))
                    .thenReturn(Optional.of(config));

            String result = configurationService.getConfig("bureti-high", "sms.sender_id");

            assertThat(result).isEqualTo("BURETI");
        }

        @Test
        @DisplayName("should decode Base64 value for sensitive config")
        void getConfig_sensitive_decodesValue() {
            String originalValue = "my-secret-passkey";
            String encodedValue = Base64.getEncoder().encodeToString(originalValue.getBytes());

            TenantConfiguration config = TenantConfiguration.builder()
                    .tenantIdentifier("bureti-high")
                    .configKey("mpesa.passkey")
                    .configValue(encodedValue)
                    .isSensitive(true)
                    .build();

            when(configurationRepository.findByTenantIdentifierAndConfigKey("bureti-high", "mpesa.passkey"))
                    .thenReturn(Optional.of(config));

            String result = configurationService.getConfig("bureti-high", "mpesa.passkey");

            assertThat(result).isEqualTo(originalValue);
        }

        @Test
        @DisplayName("should throw TenantConfigurationMissingException when config not found")
        void getConfig_notFound_throwsException() {
            when(configurationRepository.findByTenantIdentifierAndConfigKey("bureti-high", "unknown.key"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> configurationService.getConfig("bureti-high", "unknown.key"))
                    .isInstanceOf(TenantConfigurationMissingException.class)
                    .hasMessageContaining("unknown.key")
                    .hasMessageContaining("bureti-high");
        }
    }

    @Nested
    @DisplayName("getConfigOrDefault()")
    class GetConfigOrDefaultTests {

        @Test
        @DisplayName("should return config value when entry exists")
        void getConfigOrDefault_exists_returnsValue() {
            TenantConfiguration config = TenantConfiguration.builder()
                    .tenantIdentifier("bureti-high")
                    .configKey("report.header.color")
                    .configValue("#FF0000")
                    .isSensitive(false)
                    .build();

            when(configurationRepository.findByTenantIdentifierAndConfigKey("bureti-high", "report.header.color"))
                    .thenReturn(Optional.of(config));

            String result = configurationService.getConfigOrDefault("bureti-high", "report.header.color", "#000000");

            assertThat(result).isEqualTo("#FF0000");
        }

        @Test
        @DisplayName("should return default value when entry does not exist")
        void getConfigOrDefault_notFound_returnsDefault() {
            when(configurationRepository.findByTenantIdentifierAndConfigKey("bureti-high", "report.header.color"))
                    .thenReturn(Optional.empty());

            String result = configurationService.getConfigOrDefault("bureti-high", "report.header.color", "#000000");

            assertThat(result).isEqualTo("#000000");
        }
    }

    @Nested
    @DisplayName("getRequiredConfig()")
    class GetRequiredConfigTests {

        @Test
        @DisplayName("should return config value using current TenantContext")
        void getRequiredConfig_contextSet_returnsValue() {
            TenantContext.setCurrentTenant("bureti-high");

            TenantConfiguration config = TenantConfiguration.builder()
                    .tenantIdentifier("bureti-high")
                    .configKey("mpesa.shortcode")
                    .configValue("174379")
                    .isSensitive(false)
                    .build();

            when(configurationRepository.findByTenantIdentifierAndConfigKey("bureti-high", "mpesa.shortcode"))
                    .thenReturn(Optional.of(config));

            String result = configurationService.getRequiredConfig("mpesa.shortcode");

            assertThat(result).isEqualTo("174379");
        }

        @Test
        @DisplayName("should throw IllegalStateException when TenantContext is not set")
        void getRequiredConfig_noContext_throwsException() {
            assertThatThrownBy(() -> configurationService.getRequiredConfig("mpesa.shortcode"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TenantContext is not set");
        }

        @Test
        @DisplayName("should throw TenantConfigurationMissingException when config is missing")
        void getRequiredConfig_configMissing_throwsException() {
            TenantContext.setCurrentTenant("bureti-high");

            when(configurationRepository.findByTenantIdentifierAndConfigKey("bureti-high", "missing.key"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> configurationService.getRequiredConfig("missing.key"))
                    .isInstanceOf(TenantConfigurationMissingException.class);
        }
    }

    @Nested
    @DisplayName("setConfig()")
    class SetConfigTests {

        @Test
        @DisplayName("should create new config entry when it does not exist")
        void setConfig_newEntry_createsConfig() {
            when(configurationRepository.findByTenantIdentifierAndConfigKey("bureti-high", "sms.sender_id"))
                    .thenReturn(Optional.empty());

            configurationService.setConfig("bureti-high", "sms.sender_id", "BURETI", false);

            ArgumentCaptor<TenantConfiguration> captor = ArgumentCaptor.forClass(TenantConfiguration.class);
            verify(configurationRepository).save(captor.capture());

            TenantConfiguration saved = captor.getValue();
            assertThat(saved.getTenantIdentifier()).isEqualTo("bureti-high");
            assertThat(saved.getConfigKey()).isEqualTo("sms.sender_id");
            assertThat(saved.getConfigValue()).isEqualTo("BURETI");
            assertThat(saved.getIsSensitive()).isFalse();
        }

        @Test
        @DisplayName("should encode sensitive value with Base64 before storing")
        void setConfig_sensitive_encodesValue() {
            when(configurationRepository.findByTenantIdentifierAndConfigKey("bureti-high", "mpesa.passkey"))
                    .thenReturn(Optional.empty());

            configurationService.setConfig("bureti-high", "mpesa.passkey", "secret-passkey", true);

            ArgumentCaptor<TenantConfiguration> captor = ArgumentCaptor.forClass(TenantConfiguration.class);
            verify(configurationRepository).save(captor.capture());

            TenantConfiguration saved = captor.getValue();
            String expectedEncoded = Base64.getEncoder().encodeToString("secret-passkey".getBytes());
            assertThat(saved.getConfigValue()).isEqualTo(expectedEncoded);
            assertThat(saved.getIsSensitive()).isTrue();
        }

        @Test
        @DisplayName("should update existing config entry when it already exists")
        void setConfig_existingEntry_updatesConfig() {
            TenantConfiguration existing = TenantConfiguration.builder()
                    .id(1L)
                    .tenantIdentifier("bureti-high")
                    .configKey("sms.sender_id")
                    .configValue("OLD_SENDER")
                    .isSensitive(false)
                    .build();

            when(configurationRepository.findByTenantIdentifierAndConfigKey("bureti-high", "sms.sender_id"))
                    .thenReturn(Optional.of(existing));

            configurationService.setConfig("bureti-high", "sms.sender_id", "NEW_SENDER", false);

            ArgumentCaptor<TenantConfiguration> captor = ArgumentCaptor.forClass(TenantConfiguration.class);
            verify(configurationRepository).save(captor.capture());

            TenantConfiguration saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(1L);
            assertThat(saved.getConfigValue()).isEqualTo("NEW_SENDER");
        }
    }

    @Nested
    @DisplayName("getAllConfigs()")
    class GetAllConfigsTests {

        @Test
        @DisplayName("should return all configs with sensitive values decoded")
        void getAllConfigs_mixedSensitivity_decodesOnlySensitive() {
            String secretValue = "my-api-key";
            String encodedSecret = Base64.getEncoder().encodeToString(secretValue.getBytes());

            List<TenantConfiguration> configs = List.of(
                    TenantConfiguration.builder()
                            .tenantIdentifier("bureti-high")
                            .configKey("sms.sender_id")
                            .configValue("BURETI")
                            .isSensitive(false)
                            .build(),
                    TenantConfiguration.builder()
                            .tenantIdentifier("bureti-high")
                            .configKey("mpesa.passkey")
                            .configValue(encodedSecret)
                            .isSensitive(true)
                            .build()
            );

            when(configurationRepository.findByTenantIdentifier("bureti-high"))
                    .thenReturn(new java.util.ArrayList<>(configs));

            List<TenantConfiguration> result = configurationService.getAllConfigs("bureti-high");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getConfigValue()).isEqualTo("BURETI");
            assertThat(result.get(1).getConfigValue()).isEqualTo(secretValue);
        }

        @Test
        @DisplayName("should return empty list when no configs exist for tenant")
        void getAllConfigs_noConfigs_returnsEmptyList() {
            when(configurationRepository.findByTenantIdentifier("new-tenant"))
                    .thenReturn(List.of());

            List<TenantConfiguration> result = configurationService.getAllConfigs("new-tenant");

            assertThat(result).isEmpty();
        }
    }
}
