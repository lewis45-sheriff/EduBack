package com.EduePoa.EP.Staff;

import com.EduePoa.EP.Authentication.Enum.Permissions;
import com.EduePoa.EP.Authentication.Role.Role;
import com.EduePoa.EP.Authentication.Role.RolePermission;
import com.EduePoa.EP.Authentication.Role.RoleRepository;
import com.EduePoa.EP.Multitenancy.config.TenantContext;
import com.EduePoa.EP.Multitenancy.entity.Tenant;
import com.EduePoa.EP.Multitenancy.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One-time-per-boot, idempotent backfill that brings every tenant's existing {@code ROLE_TEACHER}
 * permission set up to date with the Teacher Portal features. Teacher roles created before the
 * portal added curriculum/report/compute permissions would otherwise 403 on those endpoints; this
 * runner heals them without a manual migration. Newly created teacher roles already receive the
 * full set via StaffServiceImpl, and this runner only ADDS missing permissions (never removes or
 * duplicates), so it is safe to run on every startup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(40) // after CurriculumSeeder (@Order(30)); ordering is independent but keeps logs tidy
public class TeacherRolePermissionSyncRunner implements ApplicationRunner {

    private static final String TEACHER_ROLE_NAME = "ROLE_TEACHER";

    /** Must match StaffServiceImpl.TEACHER_PERMISSIONS. */
    private static final List<Permissions> TEACHER_PERMISSIONS = List.of(
            Permissions.CLASS_READ,
            Permissions.EXAM_READ,
            Permissions.EXAM_MARK_ENTER,
            Permissions.CAT_MARK_ENTER,
            Permissions.EXAM_GRADE,
            Permissions.ATTENDANCE_MARK,
            Permissions.ATTENDANCE_READ,
            Permissions.STUDENT_READ,
            Permissions.STAFF_READ,
            Permissions.CURRICULUM_READ,
            Permissions.COMPETENCY_ASSESS,
            Permissions.REPORT_GENERATE
    );

    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void run(ApplicationArguments args) {
        List<Tenant> tenants = tenantRepository.findAll();
        for (Tenant tenant : tenants) {
            try {
                syncForTenant(tenant.getTenantIdentifier());
            } catch (Exception e) {
                log.error("Teacher role permission sync failed for tenant {}: {}",
                        tenant.getTenantIdentifier(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    protected void syncForTenant(String tenantIdentifier) {
        if (tenantIdentifier == null || tenantIdentifier.isBlank()) {
            return;
        }
        try {
            TenantContext.setCurrentTenant(tenantIdentifier);
            enableTenantFilter(tenantIdentifier);

            Role teacherRole = roleRepository.findByName(TEACHER_ROLE_NAME).orElse(null);
            if (teacherRole == null) {
                return; // tenant has no teacher role yet; nothing to heal
            }

            Set<Permissions> current = teacherRole.getRolePermissions().stream()
                    .map(RolePermission::getPermission)
                    .collect(Collectors.toSet());

            boolean changed = false;
            for (Permissions p : TEACHER_PERMISSIONS) {
                if (!current.contains(p)) {
                    teacherRole.addPermission(p);
                    changed = true;
                }
            }

            if (changed) {
                roleRepository.save(teacherRole);
                log.info("ROLE_TEACHER permissions backfilled for tenant {}", tenantIdentifier);
            }
        } finally {
            disableTenantFilter();
            TenantContext.clear();
        }
    }

    private void enableTenantFilter(String tenantId) {
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
    }

    private void disableTenantFilter() {
        try {
            entityManager.unwrap(Session.class).disableFilter("tenantFilter");
        } catch (Exception ignored) {
            // Filter may not be enabled if we bailed early; safe to ignore.
        }
    }
}
