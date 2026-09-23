package com.EduePoa.EP.Budget.Entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * A single allocation within a {@link Budget}, targeting one {@link BudgetCategory}.
 * A budget may contain at most one line per category (enforced by unique constraint
 * plus a service-level active-line check).
 */
@Entity
@Table(name = "budget_lines", uniqueConstraints = {
        @UniqueConstraint(name = "uk_budget_line_category", columnNames = {"tenant_id", "budget_id", "category_id"})
}, indexes = {
        @Index(name = "idx_budget_line_budget", columnList = "budget_id"),
        @Index(name = "idx_budget_line_category", columnList = "category_id")
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

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal allocatedAmount;

    @Version
    private Long version;
}
