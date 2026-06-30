package com.EduePoa.EP.Multitenancy.service;

import com.EduePoa.EP.Multitenancy.config.TenantNotFoundException;
import com.EduePoa.EP.Multitenancy.entity.Tenant;
import com.EduePoa.EP.Multitenancy.entity.TenantAuditLog;
import com.EduePoa.EP.Multitenancy.entity.TenantStatus;
import com.EduePoa.EP.Multitenancy.repository.TenantAuditLogRepository;
import com.EduePoa.EP.Multitenancy.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantLifecycleServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantAuditLogRepository tenantAuditLogRepository;

    @InjectMocks
    private TenantLifecycleService lifecycleService;

    private Tenant activeTenant;
    private Tenant suspendedTenant;

    @BeforeEach
    void setUp() {
        activeTenant = new Tenant();
        activeTenant.setId(1L);
        activeTenant.setTenantIdentifier("bureti-high");
        activeTenant.setSchoolName("Bureti High School");
        activeTenant.setStatus(TenantStatus.ACTIVE);
        activeTenant.setSubscriptionPlan("PREMIUM");

        suspendedTenant = new Tenant();
        suspendedTenant.setId(2L);
        suspendedTenant.setTenantIdentifier("nairobi-academy");
        suspendedTenant.setSchoolName("Nairobi Academy");
        suspendedTenant.setStatus(TenantStatus.SUSPENDED);
        suspendedTenant.setSubscriptionPlan("BASIC");
    }

    @Nested
    @DisplayName("suspend()")
    class SuspendTests {

        @Test
        @DisplayName("should set status to SUSPENDED for an active tenant")
        void suspend_activeTenant_setsStatusToSuspended() {
            when(tenantRepository.findByTenantIdentifier("bureti-high"))
                    .thenReturn(Optional.of(activeTenant));
            when(tenantRepository.save(any(Tenant.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Tenant result = lifecycleService.suspend("bureti-high", "Non-payment", "platform-admin");

            assertThat(result.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        }

        @Test
        @DisplayName("should create audit log entry on suspend")
        void suspend_activeTenant_createsAuditLog() {
            when(tenantRepository.findByTenantIdentifier("bureti-high"))
                    .thenReturn(Optional.of(activeTenant));
            when(tenantRepository.save(any(Tenant.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            lifecycleService.suspend("bureti-high", "Non-payment", "platform-admin");

            ArgumentCaptor<TenantAuditLog> captor = ArgumentCaptor.forClass(TenantAuditLog.class);
            verify(tenantAuditLogRepository).save(captor.capture());

            TenantAuditLog auditLog = captor.getValue();
            assertThat(auditLog.getTenantIdentifier()).isEqualTo("bureti-high");
            assertThat(auditLog.getAction()).isEqualTo("SUSPENDED");
            assertThat(auditLog.getActor()).isEqualTo("platform-admin");
            assertThat(auditLog.getReason()).isEqualTo("Non-payment");
        }

        @Test
        @DisplayName("should throw TenantNotFoundException for unknown tenant")
        void suspend_unknownTenant_throwsNotFound() {
            when(tenantRepository.findByTenantIdentifier("unknown"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> lifecycleService.suspend("unknown", "Test", "admin"))
                    .isInstanceOf(TenantNotFoundException.class);
        }

        @Test
        @DisplayName("should throw IllegalStateException if tenant is not ACTIVE")
        void suspend_suspendedTenant_throwsIllegalState() {
            when(tenantRepository.findByTenantIdentifier("nairobi-academy"))
                    .thenReturn(Optional.of(suspendedTenant));

            assertThatThrownBy(() -> lifecycleService.suspend("nairobi-academy", "Test", "admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot suspend");
        }
    }

    @Nested
    @DisplayName("reactivate()")
    class ReactivateTests {

        @Test
        @DisplayName("should set status to ACTIVE for a suspended tenant")
        void reactivate_suspendedTenant_setsStatusToActive() {
            when(tenantRepository.findByTenantIdentifier("nairobi-academy"))
                    .thenReturn(Optional.of(suspendedTenant));
            when(tenantRepository.save(any(Tenant.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Tenant result = lifecycleService.reactivate("nairobi-academy", "Payment received", "platform-admin");

            assertThat(result.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        }

        @Test
        @DisplayName("should create audit log entry on reactivate")
        void reactivate_suspendedTenant_createsAuditLog() {
            when(tenantRepository.findByTenantIdentifier("nairobi-academy"))
                    .thenReturn(Optional.of(suspendedTenant));
            when(tenantRepository.save(any(Tenant.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            lifecycleService.reactivate("nairobi-academy", "Payment received", "platform-admin");

            ArgumentCaptor<TenantAuditLog> captor = ArgumentCaptor.forClass(TenantAuditLog.class);
            verify(tenantAuditLogRepository).save(captor.capture());

            TenantAuditLog auditLog = captor.getValue();
            assertThat(auditLog.getTenantIdentifier()).isEqualTo("nairobi-academy");
            assertThat(auditLog.getAction()).isEqualTo("REACTIVATED");
            assertThat(auditLog.getActor()).isEqualTo("platform-admin");
            assertThat(auditLog.getReason()).isEqualTo("Payment received");
        }

        @Test
        @DisplayName("should throw TenantNotFoundException for unknown tenant")
        void reactivate_unknownTenant_throwsNotFound() {
            when(tenantRepository.findByTenantIdentifier("unknown"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> lifecycleService.reactivate("unknown", "Test", "admin"))
                    .isInstanceOf(TenantNotFoundException.class);
        }

        @Test
        @DisplayName("should throw IllegalStateException if tenant is not SUSPENDED")
        void reactivate_activeTenant_throwsIllegalState() {
            when(tenantRepository.findByTenantIdentifier("bureti-high"))
                    .thenReturn(Optional.of(activeTenant));

            assertThatThrownBy(() -> lifecycleService.reactivate("bureti-high", "Test", "admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot reactivate");
        }
    }

    @Nested
    @DisplayName("decommission()")
    class DecommissionTests {

        @Test
        @DisplayName("should set status to DECOMMISSIONED for an active tenant")
        void decommission_activeTenant_setsStatusToDecommissioned() {
            when(tenantRepository.findByTenantIdentifier("bureti-high"))
                    .thenReturn(Optional.of(activeTenant));
            when(tenantRepository.save(any(Tenant.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Tenant result = lifecycleService.decommission("bureti-high", "School closed", "platform-admin");

            assertThat(result.getStatus()).isEqualTo(TenantStatus.DECOMMISSIONED);
        }

        @Test
        @DisplayName("should set status to DECOMMISSIONED for a suspended tenant")
        void decommission_suspendedTenant_setsStatusToDecommissioned() {
            when(tenantRepository.findByTenantIdentifier("nairobi-academy"))
                    .thenReturn(Optional.of(suspendedTenant));
            when(tenantRepository.save(any(Tenant.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Tenant result = lifecycleService.decommission("nairobi-academy", "School closed", "platform-admin");

            assertThat(result.getStatus()).isEqualTo(TenantStatus.DECOMMISSIONED);
        }

        @Test
        @DisplayName("should create audit log entry on decommission")
        void decommission_activeTenant_createsAuditLog() {
            when(tenantRepository.findByTenantIdentifier("bureti-high"))
                    .thenReturn(Optional.of(activeTenant));
            when(tenantRepository.save(any(Tenant.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            lifecycleService.decommission("bureti-high", "School closed", "platform-admin");

            ArgumentCaptor<TenantAuditLog> captor = ArgumentCaptor.forClass(TenantAuditLog.class);
            verify(tenantAuditLogRepository).save(captor.capture());

            TenantAuditLog auditLog = captor.getValue();
            assertThat(auditLog.getTenantIdentifier()).isEqualTo("bureti-high");
            assertThat(auditLog.getAction()).isEqualTo("DECOMMISSIONED");
            assertThat(auditLog.getActor()).isEqualTo("platform-admin");
            assertThat(auditLog.getReason()).isEqualTo("School closed");
        }

        @Test
        @DisplayName("should throw TenantNotFoundException for unknown tenant")
        void decommission_unknownTenant_throwsNotFound() {
            when(tenantRepository.findByTenantIdentifier("unknown"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> lifecycleService.decommission("unknown", "Test", "admin"))
                    .isInstanceOf(TenantNotFoundException.class);
        }

        @Test
        @DisplayName("should throw IllegalStateException if tenant is already decommissioned")
        void decommission_decommissionedTenant_throwsIllegalState() {
            Tenant decommissionedTenant = new Tenant();
            decommissionedTenant.setId(3L);
            decommissionedTenant.setTenantIdentifier("old-school");
            decommissionedTenant.setStatus(TenantStatus.DECOMMISSIONED);
            decommissionedTenant.setSubscriptionPlan("BASIC");
            decommissionedTenant.setSchoolName("Old School");

            when(tenantRepository.findByTenantIdentifier("old-school"))
                    .thenReturn(Optional.of(decommissionedTenant));

            assertThatThrownBy(() -> lifecycleService.decommission("old-school", "Test", "admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already decommissioned");
        }
    }
}
