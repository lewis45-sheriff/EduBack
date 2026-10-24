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
@Table(name = "hr_nssf_config", indexes = {
        @Index(name = "idx_nssf_eff", columnList = "tenant_id, effective_from")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class NssfConfig extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Lower Earnings Limit (Tier I ceiling), KES. */
    @Column(name = "lower_earnings_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal lowerEarningsLimit;

    /** Upper Earnings Limit (Tier II ceiling), KES. */
    @Column(name = "upper_earnings_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal upperEarningsLimit;

    /** Employee contribution rate as a fraction (0.06 = 6%). */
    @Column(name = "employee_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal employeeRate;

    /** Employer contribution rate as a fraction (0.06 = 6%). */
    @Column(name = "employer_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal employerRate;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Version
    private Long version;
}
