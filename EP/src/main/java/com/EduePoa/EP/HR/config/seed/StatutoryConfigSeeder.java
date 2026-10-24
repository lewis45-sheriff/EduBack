package com.EduePoa.EP.HR.config.seed;

import com.EduePoa.EP.HR.config.entity.*;
import com.EduePoa.EP.HR.config.repository.*;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Idempotent, per-tenant seeder for the national-default statutory configuration.
 * <p>
 * Runs after {@code CreateAdmin} (@Order 10) so the default tenant exists, and mirrors
 * {@code CurriculumSeeder}: seeders execute outside an HTTP request, so the request-scoped
 * Hibernate tenant filter is not active — this seeder sets {@link TenantContext} and enables
 * the filter per tenant so every read/write is tenant-scoped, and the
 * {@code TenantEntityListener} stamps {@code tenant_id} from the context on persist.
 * <p>
 * <strong>Compliance note.</strong> The figures below are the Kenyan 2024/2025-reform working
 * defaults (SHIF 2.75%, AHL 1.5%/1.5%, NSSF Act 2013 Tier I/II at 6%, graduated PAYE bands,
 * KES 2,400 personal relief, NITA KES 50). They are seeded as a <em>starting point only</em> and
 * MUST be confirmed against the latest KRA/SHA/NSSF notices before go-live. Because every rate is
 * data (effective-dated rows), a statutory correction is an admin edit — never a redeploy — and a
 * correction creates a NEW effective-dated row, leaving historical payroll runs reproducible.
 * <p>
 * Idempotency: each insert is guarded by an {@code existsByEffectiveFrom} check on the seed
 * effective date, so repeated boots never create duplicates and never overwrite a tenant override.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(35)
public class StatutoryConfigSeeder implements ApplicationRunner {

    /**
     * Effective date for the seeded national defaults. The 2024 reforms (SHIF, AHL, the final
     * NSSF phase) took effect across 2024; 2024-07-01 is used as a single conservative baseline
     * for the seed. Admins add later-dated rows to reflect subsequent changes.
     */
    private static final LocalDate SEED_EFFECTIVE_FROM = LocalDate.of(2024, 7, 1);

    private final TenantRepository tenantRepository;
    private final PayeBandRepository payeBandRepository;
    private final ReliefConfigRepository reliefConfigRepository;
    private final NssfConfigRepository nssfConfigRepository;
    private final ShifConfigRepository shifConfigRepository;
    private final HousingLevyConfigRepository housingLevyConfigRepository;
    private final NitaConfigRepository nitaConfigRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void run(ApplicationArguments args) {
        List<Tenant> tenants = tenantRepository.findAll();
        for (Tenant tenant : tenants) {
            try {
                seedForTenant(tenant.getTenantIdentifier());
            } catch (Exception e) {
                log.error("Statutory config seeding failed for tenant {}: {}",
                        tenant.getTenantIdentifier(), e.getMessage(), e);
            }
        }
    }

    /** Seed (idempotently) for a single tenant. Safe to call for a newly provisioned tenant. */
    public void seedForTenant(String tenantIdentifier) {
        if (tenantIdentifier == null || tenantIdentifier.isBlank()) {
            return;
        }
        try {
            TenantContext.setCurrentTenant(tenantIdentifier);
            enableTenantFilter(tenantIdentifier);

            seedPayeBands();
            seedRelief();
            seedNssf();
            seedShif();
            seedHousingLevy();
            seedNita();

            log.info("Statutory config seeding complete for tenant {}", tenantIdentifier);
        } finally {
            disableTenantFilter();
            TenantContext.clear();
        }
    }

    private void seedPayeBands() {
        if (payeBandRepository.existsByEffectiveFrom(SEED_EFFECTIVE_FROM)) {
            return;
        }
        // Graduated monthly bands on taxable income (fractions). Confirm before go-live.
        payeBandRepository.save(band(0, "0", "24000", "0.10"));
        payeBandRepository.save(band(1, "24000.01", "32333", "0.25"));
        payeBandRepository.save(band(2, "32333.01", "500000", "0.30"));
        payeBandRepository.save(band(3, "500000.01", "800000", "0.325"));
        payeBandRepository.save(band(4, "800000.01", null, "0.35"));
    }

    private PayeBand band(int seq, String lower, String upper, String rate) {
        return PayeBand.builder()
                .sequence(seq)
                .lowerBound(new BigDecimal(lower))
                .upperBound(upper == null ? null : new BigDecimal(upper))
                .rate(new BigDecimal(rate))
                .effectiveFrom(SEED_EFFECTIVE_FROM)
                .build();
    }

    private void seedRelief() {
        if (reliefConfigRepository.existsByEffectiveFrom(SEED_EFFECTIVE_FROM)) {
            return;
        }
        reliefConfigRepository.save(ReliefConfig.builder()
                .personalReliefMonthly(new BigDecimal("2400.00"))
                .insuranceReliefRate(new BigDecimal("0.15"))
                .insuranceReliefCap(new BigDecimal("5000.00"))
                .effectiveFrom(SEED_EFFECTIVE_FROM)
                .build());
    }

    private void seedNssf() {
        if (nssfConfigRepository.existsByEffectiveFrom(SEED_EFFECTIVE_FROM)) {
            return;
        }
        // NSSF Act 2013 phased limits: LEL 8,000 / UEL 108,000 (current phase, effective Feb 2026 —
        // employee max 6% x 108,000 = 6,480). Confirm against the latest NSSF notice before go-live;
        // admins add later-dated rows for subsequent phase changes.
        nssfConfigRepository.save(NssfConfig.builder()
                .lowerEarningsLimit(new BigDecimal("8000.00"))
                .upperEarningsLimit(new BigDecimal("108000.00"))
                .employeeRate(new BigDecimal("0.06"))
                .employerRate(new BigDecimal("0.06"))
                .effectiveFrom(SEED_EFFECTIVE_FROM)
                .build());
    }

    private void seedShif() {
        if (shifConfigRepository.existsByEffectiveFrom(SEED_EFFECTIVE_FROM)) {
            return;
        }
        shifConfigRepository.save(ShifConfig.builder()
                .rate(new BigDecimal("0.0275"))
                .minimumContribution(new BigDecimal("300.00"))
                .effectiveFrom(SEED_EFFECTIVE_FROM)
                .build());
    }

    private void seedHousingLevy() {
        if (housingLevyConfigRepository.existsByEffectiveFrom(SEED_EFFECTIVE_FROM)) {
            return;
        }
        // AHL employee portion is pre-tax deductible under current rules (confirm before go-live).
        housingLevyConfigRepository.save(HousingLevyConfig.builder()
                .employeeRate(new BigDecimal("0.015"))
                .employerRate(new BigDecimal("0.015"))
                .preTaxDeductible(true)
                .reliefRate(BigDecimal.ZERO)
                .effectiveFrom(SEED_EFFECTIVE_FROM)
                .build());
    }

    private void seedNita() {
        if (nitaConfigRepository.existsByEffectiveFrom(SEED_EFFECTIVE_FROM)) {
            return;
        }
        nitaConfigRepository.save(NitaConfig.builder()
                .amountPerEmployee(new BigDecimal("50.00"))
                .effectiveFrom(SEED_EFFECTIVE_FROM)
                .build());
    }

    private void enableTenantFilter(String tenantId) {
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
    }

    private void disableTenantFilter() {
        try {
            entityManager.unwrap(Session.class).disableFilter("tenantFilter");
        } catch (Exception ignored) {
            // Filter may not be enabled if seeding bailed early; safe to ignore.
        }
    }
}
