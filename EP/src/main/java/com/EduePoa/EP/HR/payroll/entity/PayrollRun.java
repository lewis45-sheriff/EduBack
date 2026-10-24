package com.EduePoa.EP.HR.payroll.entity;

import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.HR.payroll.enums.PayrollStatus;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A monthly payroll run for a tenant, identified by {@code periodMonth}/{@code periodYear}.
 * <p>
 * Exactly one run per tenant per month/year (per-tenant unique constraint). The run carries all
 * reconciled totals and the frozen statutory + proration snapshots that produced its payslips, so
 * an APPROVED/PAID run is reproducible and immutable even after rates or the proration policy
 * change later.
 * <p>
 * Segregation of duties: {@code processedBy} (who processed) must differ from {@code approvedBy}
 * (who approved); this is enforced in the service using the authenticated actor's identity.
 * Phase 1 does NOT post to Finance — totals here are the reconciled figures a future Phase 2
 * idempotent posting will consume ({@code id} + period are the stable posting key).
 */
@Entity
@Table(name = "hr_payroll_run", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payroll_run_period",
                columnNames = {"tenant_id", "period_year", "period_month"})
}, indexes = {
        @Index(name = "idx_payroll_run_status", columnList = "tenant_id, status"),
        @Index(name = "idx_payroll_run_period", columnList = "tenant_id, period_year, period_month")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class PayrollRun extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 1-12. */
    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PayrollStatus status = PayrollStatus.DRAFT;

    // ---- Reconciled totals (recomputed on each process; frozen once APPROVED) ----
    @Column(name = "total_gross", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(name = "total_paye", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalPaye = BigDecimal.ZERO;

    @Column(name = "total_nssf_employee", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalNssfEmployee = BigDecimal.ZERO;

    @Column(name = "total_nssf_employer", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalNssfEmployer = BigDecimal.ZERO;

    @Column(name = "total_shif", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalShif = BigDecimal.ZERO;

    @Column(name = "total_ahl_employee", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalAhlEmployee = BigDecimal.ZERO;

    @Column(name = "total_ahl_employer", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalAhlEmployer = BigDecimal.ZERO;

    @Column(name = "total_nita", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalNita = BigDecimal.ZERO;

    @Column(name = "total_other_deductions", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalOtherDeductions = BigDecimal.ZERO;

    @Column(name = "total_employee_deductions", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalEmployeeDeductions = BigDecimal.ZERO;

    @Column(name = "total_employer_contributions", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalEmployerContributions = BigDecimal.ZERO;

    @Column(name = "total_net", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalNet = BigDecimal.ZERO;

    @Column(name = "total_employer_cost", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalEmployerCost = BigDecimal.ZERO;

    @Column(name = "employee_count")
    @Builder.Default
    private Integer employeeCount = 0;

    /** True when the last processing produced one or more blocking validation errors. */
    @Column(name = "has_blocking_errors")
    @Builder.Default
    private boolean hasBlockingErrors = false;

    // ---- Frozen snapshots (JSON) ----

    /** JSON-serialized {@link com.EduePoa.EP.HR.config.service.StatutoryConfigSnapshot}. */
    @Lob
    @Column(name = "statutory_snapshot", columnDefinition = "LONGTEXT")
    private String statutorySnapshotJson;

    /** JSON-serialized {@link com.EduePoa.EP.HR.proration.service.ProrationPolicySnapshot}. */
    @Lob
    @Column(name = "proration_snapshot", columnDefinition = "LONGTEXT")
    private String prorationSnapshotJson;

    /** The proration policy version used (mirrors the snapshot, for quick display/filtering). */
    @Column(name = "proration_policy_version")
    private Integer prorationPolicyVersion;

    // ---- Maker / checker ----

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approval_comment", length = 1000)
    private String approvalComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by")
    private User paidBy;

    @Column(name = "payment_reference", length = 200)
    private String paymentReference;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
