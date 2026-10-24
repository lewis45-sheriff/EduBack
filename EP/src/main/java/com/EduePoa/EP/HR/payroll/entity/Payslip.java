package com.EduePoa.EP.HR.payroll.entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.Staff.Staff;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A single employee's computed payslip within a {@link PayrollRun}. Holds the frozen computed
 * figures (not recomputed on read) and references the {@link Staff} it belongs to — this
 * ownership link is what later enables secure Phase 1.5 self-service (a staff member reads only
 * payslips whose {@code staff} resolves to their own authenticated identity).
 */
@Entity
@Table(name = "hr_payslip", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payslip_run_staff", columnNames = {"tenant_id", "payroll_run_id", "staff_id"})
}, indexes = {
        @Index(name = "idx_payslip_run", columnList = "payroll_run_id"),
        @Index(name = "idx_payslip_staff", columnList = "staff_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class Payslip extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRun payrollRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    /** Denormalised for display/reports and to keep the payslip readable even if staff detail changes. */
    @Column(name = "staff_name", length = 200)
    private String staffName;

    @Column(name = "employee_number", length = 100)
    private String employeeNumber;

    // ---- Earnings ----
    @Column(name = "basic_pay", precision = 19, scale = 2)
    private BigDecimal basicPay;

    @Column(name = "gross_pay", precision = 19, scale = 2)
    private BigDecimal grossPay;

    // ---- Statutory employee deductions ----
    @Column(name = "paye", precision = 19, scale = 2)
    private BigDecimal paye;

    @Column(name = "nssf_employee", precision = 19, scale = 2)
    private BigDecimal nssfEmployee;

    @Column(name = "shif", precision = 19, scale = 2)
    private BigDecimal shif;

    @Column(name = "ahl_employee", precision = 19, scale = 2)
    private BigDecimal ahlEmployee;

    @Column(name = "other_deductions", precision = 19, scale = 2)
    private BigDecimal otherDeductions;

    // ---- PAYE detail ----
    @Column(name = "taxable_income", precision = 19, scale = 2)
    private BigDecimal taxableIncome;

    @Column(name = "personal_relief", precision = 19, scale = 2)
    private BigDecimal personalRelief;

    @Column(name = "insurance_relief", precision = 19, scale = 2)
    private BigDecimal insuranceRelief;

    @Column(name = "total_employee_deductions", precision = 19, scale = 2)
    private BigDecimal totalEmployeeDeductions;

    @Column(name = "net_pay", precision = 19, scale = 2)
    private BigDecimal netPay;

    // ---- Employer contributions ----
    @Column(name = "nssf_employer", precision = 19, scale = 2)
    private BigDecimal nssfEmployer;

    @Column(name = "ahl_employer", precision = 19, scale = 2)
    private BigDecimal ahlEmployer;

    @Column(name = "nita", precision = 19, scale = 2)
    private BigDecimal nita;

    @Column(name = "total_employer_contributions", precision = 19, scale = 2)
    private BigDecimal totalEmployerContributions;

    // ---- Proration audit ----
    @Column(name = "prorated", nullable = false)
    @Builder.Default
    private boolean prorated = false;

    @Column(name = "eligible_days")
    private Integer eligibleDays;

    @Column(name = "total_days")
    private Integer totalDays;

    @OneToMany(mappedBy = "payslip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PayslipLine> lines = new ArrayList<>();

    @Version
    private Long version;
}
