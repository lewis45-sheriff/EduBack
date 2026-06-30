package com.EduePoa.EP.Reports;

import com.EduePoa.EP.Multitenancy.config.TenantContext;
import com.EduePoa.EP.Multitenancy.service.TenantConfigurationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportsServiceTenantBrandingTest {

    @Mock
    private TenantConfigurationService tenantConfigurationService;

    @InjectMocks
    private ReportsService reportsService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("getTenantBranding()")
    class GetTenantBrandingTests {

        @Test
        @DisplayName("should return empty map when TenantContext is not set")
        void returnsEmptyMapWhenNoTenantContext() {
            // TenantContext is not set
            Map<String, String> branding = reportsService.getTenantBranding();

            assertThat(branding).isEmpty();
            verifyNoInteractions(tenantConfigurationService);
        }

        @Test
        @DisplayName("should return all branding values when tenant has full config")
        void returnsAllBrandingWhenFullyConfigured() {
            TenantContext.setCurrentTenant("nairobi-academy");

            when(tenantConfigurationService.getConfigOrDefault("nairobi-academy", "reports.school_name", null))
                    .thenReturn("Nairobi Academy");
            when(tenantConfigurationService.getConfigOrDefault("nairobi-academy", "reports.logo_url", null))
                    .thenReturn("https://nairobi-academy.com/logo.png");
            when(tenantConfigurationService.getConfigOrDefault("nairobi-academy", "reports.address", null))
                    .thenReturn("123 Kenyatta Ave, Nairobi");

            Map<String, String> branding = reportsService.getTenantBranding();

            assertThat(branding).hasSize(3);
            assertThat(branding.get("SchoolName")).isEqualTo("Nairobi Academy");
            assertThat(branding.get("Logo")).isEqualTo("https://nairobi-academy.com/logo.png");
            assertThat(branding.get("SchoolAddress")).isEqualTo("123 Kenyatta Ave, Nairobi");
        }

        @Test
        @DisplayName("should return partial branding when tenant has partial config")
        void returnsPartialBrandingWhenPartiallyConfigured() {
            TenantContext.setCurrentTenant("bureti-high");

            when(tenantConfigurationService.getConfigOrDefault("bureti-high", "reports.school_name", null))
                    .thenReturn("Bureti High School");
            when(tenantConfigurationService.getConfigOrDefault("bureti-high", "reports.logo_url", null))
                    .thenReturn(null); // No logo configured
            when(tenantConfigurationService.getConfigOrDefault("bureti-high", "reports.address", null))
                    .thenReturn(null); // No address configured

            Map<String, String> branding = reportsService.getTenantBranding();

            assertThat(branding).hasSize(1);
            assertThat(branding.get("SchoolName")).isEqualTo("Bureti High School");
            assertThat(branding).doesNotContainKey("Logo");
            assertThat(branding).doesNotContainKey("SchoolAddress");
        }

        @Test
        @DisplayName("should return empty map when tenant has no branding config")
        void returnsEmptyMapWhenNoBrandingConfigured() {
            TenantContext.setCurrentTenant("empty-tenant");

            when(tenantConfigurationService.getConfigOrDefault("empty-tenant", "reports.school_name", null))
                    .thenReturn(null);
            when(tenantConfigurationService.getConfigOrDefault("empty-tenant", "reports.logo_url", null))
                    .thenReturn(null);
            when(tenantConfigurationService.getConfigOrDefault("empty-tenant", "reports.address", null))
                    .thenReturn(null);

            Map<String, String> branding = reportsService.getTenantBranding();

            assertThat(branding).isEmpty();
        }

        @Test
        @DisplayName("should use current tenant from TenantContext")
        void usesCurrentTenantFromContext() {
            TenantContext.setCurrentTenant("kisumu-school");

            when(tenantConfigurationService.getConfigOrDefault(eq("kisumu-school"), anyString(), isNull()))
                    .thenReturn(null);

            reportsService.getTenantBranding();

            verify(tenantConfigurationService).getConfigOrDefault("kisumu-school", "reports.school_name", null);
            verify(tenantConfigurationService).getConfigOrDefault("kisumu-school", "reports.logo_url", null);
            verify(tenantConfigurationService).getConfigOrDefault("kisumu-school", "reports.address", null);
        }
    }
}
