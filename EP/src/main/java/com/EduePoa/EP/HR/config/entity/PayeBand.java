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
@Table(name = "hr_paye_band", indexes = {
        @Index(name = "idx_paye_band_eff", columnList = "tenant_id, effective_from, sequence")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class PayeBand extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Order of the band in the schedule, ascending (0-based). */
    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    /** Inclusive lower bound of taxable income for this band. */
    @Column(name = "lower_bound", nullable = false, precision = 19, scale = 2)
    private BigDecimal lowerBound;

    /** Inclusive upper bound; {@code null} means the open-ended top band. */
    @Column(name = "upper_bound", precision = 19, scale = 2)
    private BigDecimal upperBound;

    /** Marginal tax rate for income within this band, as a fraction (0.30 = 30%). */
    @Column(name = "rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal rate;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Null = still in force. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Version
    private Long version;
}
