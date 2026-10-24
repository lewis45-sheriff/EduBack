package com.EduePoa.EP.HR.config.entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;


@Entity
@Table(name = "hr_housing_levy_config", indexes = {
        @Index(name = "idx_ahl_eff", columnList = "tenant_id, effective_from")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class HousingLevyConfig extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Employee contribution rate as a fraction of gross (0.015 = 1.5%). */
    @Column(name = "employee_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal employeeRate;

    /** Employer contribution rate as a fraction of gross (0.015 = 1.5%). */
    @Column(name = "employer_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal employerRate;

    /**
     * When true, the employee AHL is deducted from taxable income (pre-tax). When false,
     * it attracts relief at {@link #reliefRate} against PAYE payable instead. This mirrors
     * the two legislative treatments seen since the levy was introduced.
     */
    @Column(name = "pre_tax_deductible", nullable = false)
    @Builder.Default
    private boolean preTaxDeductible = true;

    /** Relief rate applied to employee AHL when not pre-tax deductible (fraction). */
    @Column(name = "relief_rate", precision = 9, scale = 6)
    @Builder.Default
    private BigDecimal reliefRate = BigDecimal.ZERO;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Version
    private Long version;
}
