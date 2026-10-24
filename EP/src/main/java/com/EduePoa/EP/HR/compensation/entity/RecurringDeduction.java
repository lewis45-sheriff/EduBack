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
import java.time.LocalDate;

/**
 * A recurring non-statutory deduction on an {@link EmployeeCompensation} (e.g. SACCO, loan,
 * salary advance, welfare). May be pre-tax (reduces taxable income) or post-tax (reduces net
 * only), fixed or a percentage of basic, with an optional active window.
 */
@Entity
@Table(name = "hr_recurring_deduction", indexes = {
        @Index(name = "idx_recurring_ded_comp", columnList = "compensation_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class RecurringDeduction extends TenantScopedEntity {

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

    /** When true, reduces PAYE taxable income; otherwise it is a post-tax deduction from net. */
    @Column(name = "pre_tax", nullable = false)
    @Builder.Default
    private boolean preTax = false;

    /** Inclusive start of the active window (null = always active from the start). */
    @Column(name = "start_date")
    private LocalDate startDate;

    /** Inclusive end of the active window (null = open-ended). */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Version
    private Long version;

    /** Whether this deduction is active for the given payroll period end date. */
    public boolean isActiveOn(LocalDate periodDate) {
        if (startDate != null && periodDate.isBefore(startDate)) {
            return false;
        }
        return endDate == null || !periodDate.isAfter(endDate);
    }
}
