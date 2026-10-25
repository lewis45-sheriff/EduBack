package com.EduePoa.EP.GeneralLedger.seed;

import com.EduePoa.EP.GeneralLedger.GlAccountKeys;
import com.EduePoa.EP.GeneralLedger.entity.GlAccount;
import com.EduePoa.EP.GeneralLedger.enums.GlAccountType;
import com.EduePoa.EP.GeneralLedger.enums.GlNormalBalance;
import com.EduePoa.EP.GeneralLedger.repository.GlAccountRepository;
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

import java.util.List;

/**
 * Idempotent, per-tenant seeder for the General Ledger chart of accounts.
 * <p>
 * Mirrors {@code StatutoryConfigSeeder}: runs after tenants exist, sets the
 * {@link TenantContext} and enables the Hibernate tenant filter per tenant (so
 * reads/writes are tenant-scoped and {@code TenantEntityListener} stamps
 * {@code tenant_id}), and guards every insert by a {@code systemKey} existence
 * check so repeated boots never duplicate accounts or overwrite an administrator
 * customization. It only ever creates missing system accounts; it never edits an
 * existing one, preserving admin changes. A partial failure for one tenant is
 * logged and does not stop the others.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(40)
public class GlAccountSeeder implements ApplicationRunner {

    private final TenantRepository tenantRepository;
    private final GlAccountRepository accountRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /** Immutable definition of a seeded system account. */
    private record Seed(String code, String name, GlAccountType type,
                        GlNormalBalance normal, String systemKey, String description) {
    }

    private static final List<Seed> ACCOUNTS = List.of(
            new Seed("1000", "Cash/Bank", GlAccountType.ASSET, GlNormalBalance.DEBIT,
                    GlAccountKeys.CASH_BANK, "Default cash & bank settlement account"),
            new Seed("1100", "Student Fees Receivable", GlAccountType.ASSET, GlNormalBalance.DEBIT,
                    GlAccountKeys.STUDENT_AR, "Student fees receivable (reserved for future accrual use)"),
            new Seed("2000", "Supplier Accounts Payable", GlAccountType.LIABILITY, GlNormalBalance.CREDIT,
                    GlAccountKeys.SUPPLIER_AP, "Amounts owed to suppliers"),
            new Seed("2100", "Net Pay Payable", GlAccountType.LIABILITY, GlNormalBalance.CREDIT,
                    GlAccountKeys.NET_PAY_PAYABLE, "Net salaries owed to employees"),
            new Seed("2200", "PAYE Payable", GlAccountType.LIABILITY, GlNormalBalance.CREDIT,
                    GlAccountKeys.PAYE_PAYABLE, "PAYE owed to KRA"),
            new Seed("2300", "NSSF Payable", GlAccountType.LIABILITY, GlNormalBalance.CREDIT,
                    GlAccountKeys.NSSF_PAYABLE, "NSSF owed (employee + employer)"),
            new Seed("2400", "SHIF Payable", GlAccountType.LIABILITY, GlNormalBalance.CREDIT,
                    GlAccountKeys.SHIF_PAYABLE, "SHIF/SHA owed"),
            new Seed("2500", "Housing Levy Payable", GlAccountType.LIABILITY, GlNormalBalance.CREDIT,
                    GlAccountKeys.AHL_PAYABLE, "Affordable Housing Levy owed (employee + employer)"),
            new Seed("2600", "NITA Payable", GlAccountType.LIABILITY, GlNormalBalance.CREDIT,
                    GlAccountKeys.NITA_PAYABLE, "NITA owed"),
            new Seed("2700", "Other Deductions Payable", GlAccountType.LIABILITY, GlNormalBalance.CREDIT,
                    GlAccountKeys.OTHER_DEDUCTIONS_PAYABLE,
                    "Aggregate non-statutory employee deductions (SACCO/loans/etc.) with no per-category split"),
            new Seed("4000", "School Fees Income", GlAccountType.INCOME, GlNormalBalance.CREDIT,
                    GlAccountKeys.FEES_INCOME, "School fees revenue"),
            new Seed("4100", "Transport Fees Income", GlAccountType.INCOME, GlNormalBalance.CREDIT,
                    GlAccountKeys.TRANSPORT_INCOME, "Transport fees revenue"),
            new Seed("5000", "Salaries and Wages Expense", GlAccountType.EXPENSE, GlNormalBalance.DEBIT,
                    GlAccountKeys.SALARIES_EXPENSE, "Gross salaries and wages"),
            new Seed("5100", "Employer Statutory Contributions", GlAccountType.EXPENSE, GlNormalBalance.DEBIT,
                    GlAccountKeys.EMPLOYER_STATUTORY_EXPENSE, "Employer NSSF + AHL + NITA"),
            new Seed("6000", "General Expenses", GlAccountType.EXPENSE, GlNormalBalance.DEBIT,
                    GlAccountKeys.EXPENSE_GENERAL, "General school expenses")
    );

    @Override
    public void run(ApplicationArguments args) {
        for (Tenant tenant : tenantRepository.findAll()) {
            try {
                seedForTenant(tenant.getTenantIdentifier());
            } catch (Exception e) {
                log.error("GL chart-of-accounts seeding failed for tenant {}: {}",
                        tenant.getTenantIdentifier(), e.getMessage(), e);
            }
        }
    }

    /** Seed (idempotently) for a single tenant. Safe for a newly provisioned tenant. */
    public void seedForTenant(String tenantIdentifier) {
        if (tenantIdentifier == null || tenantIdentifier.isBlank()) {
            return;
        }
        try {
            TenantContext.setCurrentTenant(tenantIdentifier);
            enableTenantFilter(tenantIdentifier);

            int created = 0;
            for (Seed s : ACCOUNTS) {
                // Tenant-explicit existence checks: do NOT rely on the ambient Hibernate
                // filter here, because in an ApplicationRunner/provisioning context the
                // repository calls may run in a different session where the request-scoped
                // filter is not active. A bare existsBySystemKey would then see OTHER
                // tenants' accounts and wrongly skip seeding for this tenant.
                boolean alreadyPresent =
                        accountRepository.existsByTenantIdAndSystemKey(tenantIdentifier, s.systemKey())
                                || accountRepository.existsByTenantIdAndCode(tenantIdentifier, s.code());
                if (alreadyPresent) {
                    continue; // preserve any existing/admin-customized account
                }
                accountRepository.save(GlAccount.builder()
                        .code(s.code())
                        .name(s.name())
                        .accountType(s.type())
                        .normalBalance(s.normal())
                        .systemKey(s.systemKey())
                        .active(true)
                        .systemAccount(true)
                        .description(s.description())
                        .build());
                created++;
            }
            if (created > 0) {
                log.info("GL chart-of-accounts seeded for tenant {}: {} new account(s)",
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
