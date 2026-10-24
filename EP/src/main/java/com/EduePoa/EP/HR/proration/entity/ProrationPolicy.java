package com.EduePoa.EP.HR.proration.entity;

import com.EduePoa.EP.HR.proration.enums.DayCountMethod;
import com.EduePoa.EP.HR.proration.enums.FixedAllowanceProrationRule;
import com.EduePoa.EP.HR.proration.enums.ProrationRounding;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A versioned, effective-dated mid-period proration policy for a tenant. Payroll uses the
 * policy in force on the run's period to decide how to prorate earnings for mid-month
 * joiners and leavers.
 * <p>
 * The policy is <strong>versioned</strong> (monotonically increasing {@link #version} per
 * tenant) and <strong>snapshotted</strong> onto each processed payroll run, so changing the
 * policy never alters an already-approved or paid run. Each policy row is immutable once
 * created; a change is a new row with a higher version and a later {@link #effectiveFrom}.
 * <p>
 * This models the confirmed policy dimensions:
 * <ul>
 *   <li><b>Trigger</b>: proration applies only when the employment period does not span the
 *       whole month ({@link #prorateOnJoin} / {@link #prorateOnLeave}).</li>
 *   <li><b>Date inclusivity</b>: {@link #startDateInclusive} / {@link #endDateInclusive}.</li>
 *   <li><b>Calendar vs working days</b>: {@link #dayCountMethod} (+ {@link #workingDaysMask}).</li>
 *   <li><b>Unpaid days</b>: {@link #unpaidDaysSupported} (unpaid absence days reduce eligible days).</li>
 *   <li><b>Fixed monthly allowances</b>: {@link #fixedAllowanceRule}.</li>
 *   <li><b>Rounding</b>: {@link #rounding} + {@link #roundingScale}.</li>
 *   <li><b>Effective date</b>: {@link #effectiveFrom}.</li>
 * </ul>
 */
@Entity
@Table(name = "hr_proration_policy", uniqueConstraints = {
        @UniqueConstraint(name = "uk_proration_version", columnNames = {"tenant_id", "version"})
}, indexes = {
        @Index(name = "idx_proration_eff", columnList = "tenant_id, effective_from")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class ProrationPolicy extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Monotonically increasing version per tenant; higher = newer. */
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "description", length = 500)
    private String description;

    // ---- Trigger conditions ----

    @Column(name = "prorate_on_join", nullable = false)
    @Builder.Default
    private boolean prorateOnJoin = true;

    @Column(name = "prorate_on_leave", nullable = false)
    @Builder.Default
    private boolean prorateOnLeave = true;

    // ---- Date inclusivity ----

    @Column(name = "start_date_inclusive", nullable = false)
    @Builder.Default
    private boolean startDateInclusive = true;

    @Column(name = "end_date_inclusive", nullable = false)
    @Builder.Default
    private boolean endDateInclusive = true;

    // ---- Day counting ----

    @Enumerated(EnumType.STRING)
    @Column(name = "day_count_method", nullable = false, length = 30)
    @Builder.Default
    private DayCountMethod dayCountMethod = DayCountMethod.CALENDAR_DAYS;

    /**
     * 7-char mask of which weekdays are working days, Monday..Sunday, e.g. {@code "YYYYYNN"}.
     * Only used when {@link #dayCountMethod} is WORKING_DAYS.
     */
    @Column(name = "working_days_mask", length = 7)
    @Builder.Default
    private String workingDaysMask = "YYYYYNN";

    // ---- Unpaid days ----

    @Column(name = "unpaid_days_supported", nullable = false)
    @Builder.Default
    private boolean unpaidDaysSupported = true;

    // ---- Fixed monthly allowances ----

    @Enumerated(EnumType.STRING)
    @Column(name = "fixed_allowance_rule", nullable = false, length = 30)
    @Builder.Default
    private FixedAllowanceProrationRule fixedAllowanceRule = FixedAllowanceProrationRule.PRORATE;

    // ---- Rounding ----

    @Enumerated(EnumType.STRING)
    @Column(name = "rounding", nullable = false, length = 20)
    @Builder.Default
    private ProrationRounding rounding = ProrationRounding.HALF_UP;

    @Column(name = "rounding_scale", nullable = false)
    @Builder.Default
    private int roundingScale = 2;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @Version
    private Long optimisticVersion;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
