package com.EduePoa.EP.Budget.Entity;

import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Budget.Enum.CategoryStatus;
import com.EduePoa.EP.Budget.Enum.CategoryType;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * Tenant-scoped budgeting taxonomy entry. Provides a stable category dimension for
 * budget allocations and for attributing actuals from the existing ledger.
 * <p>
 * {@code mappingKey} maps a category to a ledger {@code referenceType}
 * (e.g. "SUPPLIER_PAYMENT", "FEE_PAYMENT") so actuals can be resolved without
 * re-scanning source tables and without double counting.
 */
@Entity
@Table(name = "budget_categories", uniqueConstraints = {
        @UniqueConstraint(name = "uk_budget_category_name", columnNames = {"tenant_id", "name", "status"})
}, indexes = {
        @Index(name = "idx_budget_category_type", columnList = "tenant_id, type"),
        @Index(name = "idx_budget_category_mapping", columnList = "tenant_id, mapping_key")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class BudgetCategory extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoryType type;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CategoryStatus status = CategoryStatus.ACTIVE;

    /**
     * Ledger referenceType this category maps to (e.g. "SUPPLIER_PAYMENT", "FEE_PAYMENT").
     * Nullable: an unmapped category resolves to zero actuals until a mapping is set.
     */
    @Column(name = "mapping_key", length = 100)
    private String mappingKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
