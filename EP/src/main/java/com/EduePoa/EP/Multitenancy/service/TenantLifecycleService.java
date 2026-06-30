package com.EduePoa.EP.Multitenancy.service;

import com.EduePoa.EP.Multitenancy.config.TenantNotFoundException;
import com.EduePoa.EP.Multitenancy.entity.Tenant;
import com.EduePoa.EP.Multitenancy.entity.TenantAuditLog;
import com.EduePoa.EP.Multitenancy.entity.TenantStatus;
import com.EduePoa.EP.Multitenancy.repository.TenantAuditLogRepository;
import com.EduePoa.EP.Multitenancy.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class TenantLifecycleService {

    private static final String ACTION_SUSPENDED = "SUSPENDED";
    private static final String ACTION_REACTIVATED = "REACTIVATED";
    private static final String ACTION_DECOMMISSIONED = "DECOMMISSIONED";

    private final TenantRepository tenantRepository;
    private final TenantAuditLogRepository tenantAuditLogRepository;


    @Transactional
    public Tenant suspend(String tenantId, String reason, String actor) {
        Tenant tenant = findTenantOrThrow(tenantId);

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot suspend tenant '" + tenantId + "': current status is " + tenant.getStatus());
        }

        tenant.setStatus(TenantStatus.SUSPENDED);
        Tenant savedTenant = tenantRepository.save(tenant);

        logAuditEntry(tenantId, ACTION_SUSPENDED, actor, reason);
        log.info("Tenant suspended: identifier={}, actor={}, reason={}", tenantId, actor, reason);

        return savedTenant;
    }


    @Transactional
    public Tenant reactivate(String tenantId, String reason, String actor) {
        Tenant tenant = findTenantOrThrow(tenantId);

        if (tenant.getStatus() != TenantStatus.SUSPENDED) {
            throw new IllegalStateException(
                    "Cannot reactivate tenant '" + tenantId + "': current status is " + tenant.getStatus());
        }

        tenant.setStatus(TenantStatus.ACTIVE);
        Tenant savedTenant = tenantRepository.save(tenant);

        logAuditEntry(tenantId, ACTION_REACTIVATED, actor, reason);
        log.info("Tenant reactivated: identifier={}, actor={}, reason={}", tenantId, actor, reason);

        return savedTenant;
    }


    @Transactional
    public Tenant decommission(String tenantId, String reason, String actor) {
        Tenant tenant = findTenantOrThrow(tenantId);

        if (tenant.getStatus() == TenantStatus.DECOMMISSIONED) {
            throw new IllegalStateException(
                    "Cannot decommission tenant '" + tenantId + "': tenant is already decommissioned");
        }

        tenant.setStatus(TenantStatus.DECOMMISSIONED);
        Tenant savedTenant = tenantRepository.save(tenant);

        logAuditEntry(tenantId, ACTION_DECOMMISSIONED, actor, reason);
        log.info("Tenant decommissioned: identifier={}, actor={}, reason={}", tenantId, actor, reason);

        return savedTenant;
    }


    private Tenant findTenantOrThrow(String tenantId) {
        return tenantRepository.findByTenantIdentifier(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(
                        "Tenant not found with identifier: " + tenantId, tenantId));
    }

    private void logAuditEntry(String tenantId, String action, String actor, String reason) {
        TenantAuditLog auditLog = TenantAuditLog.builder()
                .tenantIdentifier(tenantId)
                .action(action)
                .actor(actor)
                .reason(reason)
                .build();

        tenantAuditLogRepository.save(auditLog);
    }
}
