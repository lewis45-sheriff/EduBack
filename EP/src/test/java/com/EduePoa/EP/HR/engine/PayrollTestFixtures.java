package com.EduePoa.EP.HR.engine;

import com.EduePoa.EP.HR.config.service.StatutoryConfigSnapshot;
import com.EduePoa.EP.HR.proration.enums.DayCountMethod;
import com.EduePoa.EP.HR.proration.enums.FixedAllowanceProrationRule;
import com.EduePoa.EP.HR.proration.enums.ProrationRounding;
import com.EduePoa.EP.HR.proration.service.ProrationPolicySnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Shared builders for the national-default statutory and proration snapshots used across the
 * HR engine and proration tests. Mirrors what {@code StatutoryConfigSeeder} seeds, so the engine
 * tests exercise the same figures an out-of-the-box tenant would use.
 */
public final class PayrollTestFixtures {

    private PayrollTestFixtures() {
    }

    public static StatutoryConfigSnapshot defaultStatutory() {
        return StatutoryConfigSnapshot.builder()
                .asOf(LocalDate.of(2025, 1, 1))
                .payeBands(List.of(
                        band(0, "0", "24000", "0.10"),
                        band(1, "24000.01", "32333", "0.25"),
                        band(2, "32333.01", "500000", "0.30"),
                        band(3, "500000.01", "800000", "0.325"),
                        band(4, "800000.01", null, "0.35")))
                .personalReliefMonthly(new BigDecimal("2400.00"))
                .insuranceReliefRate(new BigDecimal("0.15"))
                .insuranceReliefCap(new BigDecimal("5000.00"))
                .nssfLowerEarningsLimit(new BigDecimal("8000.00"))
                .nssfUpperEarningsLimit(new BigDecimal("72000.00"))
                .nssfEmployeeRate(new BigDecimal("0.06"))
                .nssfEmployerRate(new BigDecimal("0.06"))
                .shifRate(new BigDecimal("0.0275"))
                .shifMinimumContribution(new BigDecimal("300.00"))
                .ahlEmployeeRate(new BigDecimal("0.015"))
                .ahlEmployerRate(new BigDecimal("0.015"))
                .ahlPreTaxDeductible(true)
                .ahlReliefRate(BigDecimal.ZERO)
                .nitaAmountPerEmployee(new BigDecimal("50.00"))
                .build();
    }

    public static ProrationPolicySnapshot calendarPolicy() {
        return ProrationPolicySnapshot.builder()
                .policyVersion(1)
                .effectiveFrom(LocalDate.of(2024, 1, 1))
                .prorateOnJoin(true)
                .prorateOnLeave(true)
                .startDateInclusive(true)
                .endDateInclusive(true)
                .dayCountMethod(DayCountMethod.CALENDAR_DAYS)
                .workingDaysMask("YYYYYNN")
                .unpaidDaysSupported(true)
                .fixedAllowanceRule(FixedAllowanceProrationRule.PRORATE)
                .rounding(ProrationRounding.HALF_UP)
                .roundingScale(2)
                .build();
    }

    private static StatutoryConfigSnapshot.Band band(int seq, String lo, String hi, String rate) {
        return StatutoryConfigSnapshot.Band.builder()
                .sequence(seq)
                .lowerBound(new BigDecimal(lo))
                .upperBound(hi == null ? null : new BigDecimal(hi))
                .rate(new BigDecimal(rate))
                .build();
    }
}
