package com.EduePoa.EP.OrgUnit.Seed;

import com.EduePoa.EP.Multitenancy.config.TenantContext;
import com.EduePoa.EP.Multitenancy.entity.Tenant;
import com.EduePoa.EP.Multitenancy.repository.TenantRepository;
import com.EduePoa.EP.OrgUnit.Entity.OrgUnit;
import com.EduePoa.EP.OrgUnit.Entity.OrgUnitStatus;
import com.EduePoa.EP.OrgUnit.Entity.OrgUnitType;
import com.EduePoa.EP.OrgUnit.Repository.OrgUnitRepository;
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

import java.time.LocalDateTime;
import java.util.List;

/**
 * Idempotent, per-tenant seeder for the default organizational hierarchy (design §5.3).
 * <p>
 * Mirrors {@code GlAccountSeeder}: sets {@link TenantContext}, enables the Hibernate tenant filter,
 * guards every insert by a tenant-explicit {@code code} existence check, and restores context in a
 * {@code finally} block. It only ever creates missing units; it never renames, moves or overwrites
 * an administrator-customized unit, and resolves existing units by their stable {@code code}. A
 * failure for one tenant is logged and never blocks the others.
 *
 * <p>The default tree is a starting point, not a mandatory taxonomy: a root derived from the
 * tenant's school name and an editable set of departments. Administrators may rename, move, extend
 * or deactivate any of them afterward.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(50)
public class OrgUnitSeeder implements ApplicationRunner {

    /** Stable code of the seeded root; used to resolve/anchor children idempotently. */
    public static final String ROOT_CODE = "SCHOOL";

    /** Editable default departments: {code, name}. Change here to change the default taxonomy. */
    private static final List<String[]> DEFAULT_DEPARTMENTS = List.of(
            new String[]{"ACADEMICS", "Academics"},
            new String[]{"ADMIN", "Administration"},
            new String[]{"FINANCE", "Finance"},
            new String[]{"TRANSPORT", "Transport"},
            new String[]{"CATERING", "Catering / Meals"},
            new String[]{"BOARDING", "Boarding"},
            new String[]{"MAINTENANCE", "Maintenance / Operations"},
            new String[]{"ICT", "ICT"}
    );

    private final TenantRepository tenantRepository;
    private final OrgUnitRepository orgUnitRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void run(ApplicationArguments args) {
        for (Tenant tenant : tenantRepository.findAll()) {
            try {
                seedForTenant(tenant.getTenantIdentifier(), tenant.getSchoolName());
            } catch (Exception e) {
                log.error("OrgUnit seeding failed for tenant {}: {}",
                        tenant.getTenantIdentifier(), e.getMessage(), e);
            }
        }
    }

    /** Convenience overload resolving the school name from the tenant record. */
    public void seedForTenant(String tenantIdentifier) {
        Tenant tenant = tenantRepository.findByTenantIdentifier(tenantIdentifier).orElse(null);
        seedForTenant(tenantIdentifier, tenant != null ? tenant.getSchoolName() : null);
    }

    @Transactional
    public void seedForTenant(String tenantIdentifier, String schoolName) {
        if (tenantIdentifier == null || tenantIdentifier.isBlank()) {
            return;
        }
        try {
            TenantContext.setCurrentTenant(tenantIdentifier);
            enableTenantFilter(tenantIdentifier);

            // Resolve or create the single root. Idempotent by ROOT_CODE; never overwrites a
            // renamed root (admin may have renamed the display name — the code stays stable).
            OrgUnit root = orgUnitRepository.findByCode(ROOT_CODE).orElse(null);
            if (root == null && !orgUnitRepository.findRootsForTenant(tenantIdentifier, OrgUnitStatus.ACTIVE).isEmpty()) {
                // An admin already created a root with a different code; do not add a second one.
                log.info("OrgUnit seeding skipped for tenant {}: a root already exists", tenantIdentifier);
                return;
            }
            if (root == null) {
                String rootName = (schoolName != null && !schoolName.isBlank()) ? schoolName.trim() : "School";
                root = orgUnitRepository.save(OrgUnit.builder()
                        .name(rootName)
                        .code(ROOT_CODE)
                        .type(OrgUnitType.SCHOOL)
                        .parent(null)
                        .status(OrgUnitStatus.ACTIVE)
                        .depth(0)
                        .path("/")
                        .createdAt(LocalDateTime.now())
                        .build());
                root.setPath("/" + root.getId() + "/");
                root = orgUnitRepository.save(root);
                log.info("OrgUnit root seeded for tenant {}: {}", tenantIdentifier, rootName);
            }

            int created = 0;
            for (String[] dept : DEFAULT_DEPARTMENTS) {
                String code = dept[0];
                if (orgUnitRepository.existsByTenantIdAndCode(tenantIdentifier, code)) {
                    continue; // preserve admin changes; never duplicate
                }
                OrgUnit child = orgUnitRepository.save(OrgUnit.builder()
                        .name(dept[1])
                        .code(code)
                        .type(OrgUnitType.DEPARTMENT)
                        .parent(root)
                        .status(OrgUnitStatus.ACTIVE)
                        .depth(1)
                        .path(root.getPath()) // temporary; finalized below
                        .createdAt(LocalDateTime.now())
                        .build());
                child.setPath(root.getPath() + child.getId() + "/");
                orgUnitRepository.save(child);
                created++;
            }
            if (created > 0) {
                log.info("OrgUnit default departments seeded for tenant {}: {} new unit(s)",
                        tenantIdentifier, created);
            }
        } finally {
            disableTenantFilter();
            TenantContext.clear();
        }
    }

    private void enableTenantFilter(String tenantId) {
        entityManager.unwrap(Session.class).enableFilter("tenantFilter").setParameter("tenantId", tenantId);
    }

    private void disableTenantFilter() {
        try {
            entityManager.unwrap(Session.class).disableFilter("tenantFilter");
        } catch (Exception ignored) {
            // not enabled
        }
    }
}
