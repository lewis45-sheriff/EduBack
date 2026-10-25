package com.EduePoa.EP.Budget.Entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.OrgUnit.Entity.OrgUnit;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * A single allocation within a {@link Budget}, targeting one {@link BudgetCategory} and an optional
 * {@link OrgUnit}. A budget may contain at most one line per {@code (category, orgUnit)} pair.
 * <p>
 * The database unique key includes {@code org_unit_id}, but MariaDB treats NULLs as distinct in a
 * unique index, so the "school-wide" case (null org unit) is additionally enforced in the service
 * layer ({@code BudgetServiceImpl}) to guarantee at most one unassigned line per category.
 */
@Entity
@Table(name = "budget_lines", uniqueConstraints = {
        @UniqueConstraint(name = "uk_budget_line_category_unit", columnNames = {"tenant_id", "budget_id", "category_id", "org_unit_id"})
}, indexes = {
        @Index(name = "idx_budget_line_budget", columnList = "budget_id"),
        @Index(name = "idx_budget_line_category", columnList = "category_id"),
        @Index(name = "idx_budget_line_org_unit", columnList = "org_unit_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class BudgetLine extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private BudgetCategory category;

    /**
     * Optional organizational unit this allocation targets. Null means school-wide / unassigned
     * (an explicit, single "no org unit" line per category — never silently spread across units).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_unit_id")
    private OrgUnit orgUnit;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal allocatedAmount;

    /**
     * The allocation at the moment the budget was approved, captured so the original approved
     * baseline stays visible even after amendments change {@link #allocatedAmount} (design §9).
     * Null for lines that have never been part of an approved budget (pure DRAFT).
     */
    @Column(name = "original_allocated_amount", precision = 19, scale = 2)
    private BigDecimal originalAllocatedAmount;

    @Version
    private Long version;
}
