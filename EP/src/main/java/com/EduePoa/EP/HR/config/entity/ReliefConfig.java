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
@Table(name = "hr_relief_config", indexes = {
        @Index(name = "idx_relief_eff", columnList = "tenant_id, effective_from")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class ReliefConfig extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Monthly personal relief (KES), subtracted from gross PAYE for every employee. */
    @Column(name = "personal_relief_monthly", nullable = false, precision = 19, scale = 2)
    private BigDecimal personalReliefMonthly;

    /** Insurance relief as a fraction of qualifying premiums (e.g. 0.15 = 15%). */
    @Column(name = "insurance_relief_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal insuranceReliefRate;

    /** Monthly cap on insurance relief (KES). */
    @Column(name = "insurance_relief_cap", nullable = false, precision = 19, scale = 2)
    private BigDecimal insuranceReliefCap;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Version
    private Long version;
}
