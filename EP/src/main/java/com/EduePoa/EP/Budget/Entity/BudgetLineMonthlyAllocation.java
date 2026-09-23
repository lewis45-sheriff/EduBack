package com.EduePoa.EP.Budget.Entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Year;

/**
 * Optional monthly phasing of a {@link BudgetLine}'s allocation. The sum of monthly
 * allocations for a line must not exceed the line's allocatedAmount (under-phasing allowed).
 */
@Entity
@Table(name = "budget_line_monthly_allocations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_budget_line_month", columnNames = {"tenant_id", "budget_line_id", "year_value", "month_value"})
}, indexes = {
        @Index(name = "idx_budget_line_monthly_line", columnList = "budget_line_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class BudgetLineMonthlyAllocation extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_line_id", nullable = false)
    private BudgetLine budgetLine;

    /** Calendar month 1..12. */
    @Column(name = "month_value", nullable = false)
    private int monthValue;

    /** Calendar year the month belongs to (a budget window may span two calendar years). */
    @Column(name = "year_value", nullable = false)
    private Year yearValue;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal allocatedAmount;
}
