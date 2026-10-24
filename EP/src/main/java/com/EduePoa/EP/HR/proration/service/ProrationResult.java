package com.EduePoa.EP.HR.proration.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * The outcome of applying a proration policy to one employee's eligible employment period
 * within a payroll month.
 */
@Data
@AllArgsConstructor
@Builder
public class ProrationResult {

    /** True when the employee is employed for the whole period and no proration is needed. */
    private boolean fullPeriod;

    /** Eligible days actually worked/paid within the period (after unpaid-day removal). */
    private int eligibleDays;

    /** Total days in the period under the policy's day-count method (the proration denominator). */
    private int totalDays;

    /**
     * The proration fraction = eligibleDays / totalDays, unrounded (high scale) for use as a
     * multiplier on money amounts. {@code BigDecimal.ONE} when {@link #fullPeriod} is true.
     */
    private BigDecimal fraction;

    /** True when the employee has zero eligible days in the period (nothing payable). */
    public boolean isZeroEligible() {
        return eligibleDays <= 0;
    }
}
