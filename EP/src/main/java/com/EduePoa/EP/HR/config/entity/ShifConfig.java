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
@Table(name = "hr_shif_config", indexes = {
        @Index(name = "idx_shif_eff", columnList = "tenant_id, effective_from")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class ShifConfig extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Contribution rate as a fraction of gross (0.0275 = 2.75%). */
    @Column(name = "rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal rate;

    /** Minimum monthly contribution (KES), applied when the computed rate falls below it. */
    @Column(name = "minimum_contribution", nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumContribution;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Version
    private Long version;
}
