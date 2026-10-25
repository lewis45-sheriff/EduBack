package com.EduePoa.EP.GeneralLedger.seed;

import com.EduePoa.EP.Authentication.Enum.Permissions;
import com.EduePoa.EP.Authentication.Role.Role;
import com.EduePoa.EP.Authentication.Role.RoleRepository;
import com.EduePoa.EP.Multitenancy.config.TenantContext;
import com.EduePoa.EP.Multitenancy.entity.Tenant;
import com.EduePoa.EP.Multitenancy.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One-time, idempotent backfill that grants the General Ledger permissions (and
 * the pre-existing {@code ledger:read}) to each existing tenant's
 * {@code School_Admin} role.
 * <p>
 * New tenants receive every non-platform permission automatically at provisioning
 * ({@code TenantProvisioningService.createDefaultAdminUser}), but that only runs
 * when the role is first created. Existing tenants' roles already exist, so this
 * runner adds just the permissions they are missing. It never removes a
 * permission and never touches any other role, so it is safe to run on every boot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(45)
public class GlPermissionBackfillRunner implements ApplicationRunner {

    private static final String SCHOOL_ADMIN_ROLE = "School_Admin";

    private static final List<Permissions> GL_PERMISSIONS = List.of(
            Permissions.GL_READ,
            Permissions.GL_POST,
            Permissions.GL_MANAGE,
            Permissions.JOURNAL_REVERSAL_CREATE,
            Permissions.JOURNAL_REVERSAL_APPROVE,
            Permissions.GL_RECONCILE,
            // Also backfill the pre-existing single-entry ledger permission, which the
            // /api/v1/ledger endpoints require but which was never present in the
            // Permissions enum (so existing School_Admins were getting 403s on it).
            Permissions.LEDGER_READ
    );

    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;

    @Override
    public void run(ApplicationArguments args) {
        for (Tenant tenant : tenantRepository.findAll()) {
            try {
                backfill(tenant.getTenantIdentifier());
            } catch (Exception e) {
                log.error("GL permission backfill failed for tenant {}: {}",
                        tenant.getTenantIdentifier(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void backfill(String tenantIdentifier) {
        if (tenantIdentifier == null || tenantIdentifier.isBlank()) {
            return;
        }
        try {
            TenantContext.setCurrentTenant(tenantIdentifier);
            Role admin = roleRepository.findByNameAndTenantId(SCHOOL_ADMIN_ROLE, tenantIdentifier).orElse(null);
            if (admin == null) {
                return;
            }
            Set<Permissions> existing = admin.getRolePermissions().stream()
                    .map(rp -> rp.getPermission())
                    .collect(Collectors.toSet());

            int added = 0;
            for (Permissions p : GL_PERMISSIONS) {
                if (!existing.contains(p)) {
                    admin.addPermission(p);
                    added++;
                }
            }
            if (added > 0) {
                roleRepository.save(admin);
                log.info("Granted {} GL permission(s) to School_Admin for tenant {}", added, tenantIdentifier);
            }
        } finally {
            TenantContext.clear();
        }
    }
}
