package com.EduePoa.EP.HR.compensation.entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.Staff.Staff;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "hr_employee_compensation", uniqueConstraints = {
        @UniqueConstraint(name = "uk_emp_comp_staff", columnNames = {"tenant_id", "staff_id"})
}, indexes = {
        @Index(name = "idx_emp_comp_staff", columnList = "staff_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class EmployeeCompensation extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Column(name = "basic_salary", nullable = false, precision = 19, scale = 2)
    private BigDecimal basicSalary;

    @Column(name = "currency", length = 3, nullable = false)
    @Builder.Default
    private String currency = "KES";

    @Column(name = "nssf_pensionable_pay_override", precision = 19, scale = 2)
    private BigDecimal nssfPensionablePayOverride;

    @Column(name = "insurance_premium_monthly", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal insurancePremiumMonthly = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @OneToMany(mappedBy = "compensation", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<Allowance> allowances = new ArrayList<>();

    @OneToMany(mappedBy = "compensation", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<RecurringDeduction> recurringDeductions = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
