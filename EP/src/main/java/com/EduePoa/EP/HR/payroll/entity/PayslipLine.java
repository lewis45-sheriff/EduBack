package com.EduePoa.EP.HR.payroll.entity;

import com.EduePoa.EP.HR.payroll.enums.PayslipLineType;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * One itemised line on a {@link Payslip} — the human-readable breakdown (earnings, statutory
 * deductions, other deductions, employer contributions) used for display and audit.
 */
@Entity
@Table(name = "hr_payslip_line", indexes = {
        @Index(name = "idx_payslip_line_payslip", columnList = "payslip_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class PayslipLine extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payslip_id", nullable = false)
    private Payslip payslip;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 30)
    private PayslipLineType lineType;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
