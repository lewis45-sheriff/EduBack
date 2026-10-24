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

/**
 * NITA (training levy) configuration, effective-dated and tenant-overridable. An
 * employer-side fixed amount per employee per month. Tracked as an employer cost and in
 * remittance reports; never deducted from the employee.
 */
@Entity
@Table(name = "hr_nita_config", indexes = {
        @Index(name = "idx_nita_eff", columnList = "tenant_id, effective_from")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class NitaConfig extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Fixed employer levy per employee per month (KES). */
    @Column(name = "amount_per_employee", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPerEmployee;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Version
    private Long version;
}
