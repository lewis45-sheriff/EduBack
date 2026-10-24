package com.EduePoa.EP.HR.compensation.entity;

import com.EduePoa.EP.HR.compensation.enums.AmountBasis;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;



@Entity
@Table(name = "hr_allowance", indexes = {
        @Index(name = "idx_allowance_comp", columnList = "compensation_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class Allowance extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compensation_id", nullable = false)
    private EmployeeCompensation compensation;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", nullable = false, length = 30)
    @Builder.Default
    private AmountBasis basis = AmountBasis.FIXED;

    /** Fixed amount (when basis = FIXED) or percentage as a fraction (when PERCENTAGE_OF_BASIC). */
    @Column(name = "amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    @Column(name = "taxable", nullable = false)
    @Builder.Default
    private boolean taxable = true;

    @Column(name = "pensionable", nullable = false)
    @Builder.Default
    private boolean pensionable = false;

    /**
     * Whether this is a fixed monthly allowance for proration purposes. When true and the
     * proration policy says fixed allowances are NOT prorated, the full amount is paid even
     * in a partial month. When false, the allowance is prorated alongside basic salary.
     */
    @Column(name = "fixed_monthly", nullable = false)
    @Builder.Default
    private boolean fixedMonthly = false;

    @Version
    private Long version;
}
