package com.EduePoa.EP.Budget.Entity;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Budget.Enum.BudgetStatus;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;

/**
 * Tenant-scoped financial plan for a period. Supports an academic period
 * (term + year) and/or an explicit date range; {@code [startDate, endDate]} is
 * always the authoritative window for attributing actuals.
 */
@Entity
@Table(name = "budgets", indexes = {
        @Index(name = "idx_budget_status", columnList = "tenant_id, status"),
        @Index(name = "idx_budget_academic", columnList = "tenant_id, academic_year, academic_term"),
        @Index(name = "idx_budget_window", columnList = "tenant_id, start_date, end_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class Budget extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "academic_term", length = 20)
    private Term academicTerm;

    @Column(name = "academic_year")
    private Year academicYear;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BudgetStatus status = BudgetStatus.DRAFT;

    @Column(name = "total_income_allocated", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalIncomeAllocated = BigDecimal.ZERO;

    @Column(name = "total_expense_allocated", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalExpenseAllocated = BigDecimal.ZERO;

    /**
     * Optional per-budget override of the APPROACHING threshold (percentage). When null the
     * tenant/system default from {@code BudgetThresholdConfig} applies (design §7.2).
     */
    @Column(name = "approaching_threshold_percent", precision = 5, scale = 2)
    private BigDecimal approachingThresholdPercent;

    /** Optional per-budget override of the OVER threshold (percentage). Null = use default. */
    @Column(name = "over_threshold_percent", precision = 5, scale = 2)
    private BigDecimal overThresholdPercent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private User closedBy;

    @Column(name = "approval_comment", length = 1000)
    private String approvalComment;

    @Column(name = "closure_comment", length = 1000)
    private String closureComment;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime approvedAt;

    private LocalDateTime closedAt;

    @Version
    private Long version;
}
