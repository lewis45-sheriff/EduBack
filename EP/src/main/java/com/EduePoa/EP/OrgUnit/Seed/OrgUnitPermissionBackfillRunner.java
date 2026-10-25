package com.EduePoa.EP.OrgUnit.Seed;

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

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Idempotent backfill granting the new {@code org_unit:manage} permission to each existing tenant's
 * {@code School_Admin} role. New tenants receive it automatically at provisioning (the role is
 * created with every non-platform permission); existing roles pre-date the permission, so this adds
 * only what is missing. Never removes a permission and never touches other roles.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(51)
public class OrgUnitPermissionBackfillRunner implements ApplicationRunner {

    private static final String SCHOOL_ADMIN_ROLE = "School_Admin";

    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;

    @Override
    public void run(ApplicationArguments args) {
        for (Tenant tenant : tenantRepository.findAll()) {
            try {
                backfill(tenant.getTenantIdentifier());
            } catch (Exception e) {
                log.error("OrgUnit permission backfill failed for tenant {}: {}",
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
            if (!existing.contains(Permissions.ORG_UNIT_MANAGE)) {
                admin.addPermission(Permissions.ORG_UNIT_MANAGE);
                roleRepository.save(admin);
                log.info("Granted org_unit:manage to School_Admin for tenant {}", tenantIdentifier);
            }
        } finally {
            TenantContext.clear();
        }
    }
}
