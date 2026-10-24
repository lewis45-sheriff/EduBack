package com.EduePoa.EP.HR.payroll.entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;

/**
 * Per-run, per-employee employment facts that drive proration for one payroll period: an optional
 * joining-date override (defaults to the staff member's {@code dateOfEmployment}), an optional
 * leaving date, and a count of unpaid days. These are captured here — not on {@code Staff} or
 * {@code EmployeeCompensation} — so recording period-specific facts (a mid-month leaver, unpaid
 * absence) never modifies contractual salary history or any previously approved run.
 * <p>
 * One adjustment per {@code staff} per {@code payrollRun} (per-tenant unique constraint).
 */
@Entity
@Table(name = "hr_payroll_employee_adjustment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payroll_adj_run_staff",
                columnNames = {"tenant_id", "payroll_run_id", "staff_id"})
}, indexes = {
        @Index(name = "idx_payroll_adj_run", columnList = "payroll_run_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class PayrollEmployeeAdjustment extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payroll_run_id", nullable = false)
    private Long payrollRunId;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    /** Overrides the staff member's dateOfEmployment for this period's proration (optional). */
    @Column(name = "join_date")
    private LocalDate joinDate;

    /** Leaving date within (or before) the period, for a mid-month leaver (optional). */
    @Column(name = "leave_date")
    private LocalDate leaveDate;

    /** Unpaid/absence days to deduct from eligible days (0 when none). */
    @Column(name = "unpaid_days", nullable = false)
    @Builder.Default
    private int unpaidDays = 0;

    @Version
    private Long version;
}
