package com.EduePoa.EP.HR.proration.service;

import com.EduePoa.EP.HR.proration.entity.ProrationPolicy;
import com.EduePoa.EP.HR.proration.enums.DayCountMethod;
import com.EduePoa.EP.HR.proration.repository.ProrationPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Resolves the versioned proration policy for a tenant and computes the eligible-days
 * proration for one employee within a payroll month.
 * <p>
 * The day-counting logic is a pure function of the {@link ProrationPolicySnapshot} plus the
 * employment dates and the period, so it can be driven equally by the live policy (first
 * processing) or by the frozen snapshot stored on a run (deterministic reprocessing). This is
 * the mechanism that keeps approved/paid runs reproducible after a policy change.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProrationService {

    /** High-precision context for the fraction before the engine rounds money. */
    private static final MathContext FRACTION_CONTEXT = new MathContext(16);

    private final ProrationPolicyRepository policyRepository;

    /**
     * Resolve the proration policy in force on {@code asOf} for the current tenant and return it
     * as an immutable snapshot. If no policy exists, a documented built-in default is returned
     * (calendar-day proration, inclusive dates, fixed allowances prorated, HALF_UP to 2dp) and
     * recorded as policy version 0 so the run still carries a snapshot.
     */
    public ProrationPolicySnapshot resolveSnapshot(LocalDate asOf) {
        ProrationPolicy policy = policyRepository
                .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(asOf, Limit.of(1))
                .orElse(null);
        if (policy == null) {
            return defaultSnapshot(asOf);
        }
        return ProrationPolicySnapshot.builder()
                .policyVersion(policy.getVersion())
                .effectiveFrom(policy.getEffectiveFrom())
                .prorateOnJoin(policy.isProrateOnJoin())
                .prorateOnLeave(policy.isProrateOnLeave())
                .startDateInclusive(policy.isStartDateInclusive())
                .endDateInclusive(policy.isEndDateInclusive())
                .dayCountMethod(policy.getDayCountMethod())
                .workingDaysMask(policy.getWorkingDaysMask())
                .unpaidDaysSupported(policy.isUnpaidDaysSupported())
                .fixedAllowanceRule(policy.getFixedAllowanceRule())
                .rounding(policy.getRounding())
                .roundingScale(policy.getRoundingScale())
                .build();
    }

    /**
     * The documented default policy used when a tenant has configured none. It is deliberately
     * explicit (not silently "calendar days are always right"): admins are expected to review and
     * save a policy. Version 0 marks it as the system default rather than a stored decision.
     */
    public ProrationPolicySnapshot defaultSnapshot(LocalDate asOf) {
        return ProrationPolicySnapshot.builder()
                .policyVersion(0)
                .effectiveFrom(asOf != null ? asOf.withDayOfMonth(1) : null)
                .prorateOnJoin(true)
                .prorateOnLeave(true)
                .startDateInclusive(true)
                .endDateInclusive(true)
                .dayCountMethod(DayCountMethod.CALENDAR_DAYS)
                .workingDaysMask("YYYYYNN")
                .unpaidDaysSupported(true)
                .fixedAllowanceRule(com.EduePoa.EP.HR.proration.enums.FixedAllowanceProrationRule.PRORATE)
                .rounding(com.EduePoa.EP.HR.proration.enums.ProrationRounding.HALF_UP)
                .roundingScale(2)
                .build();
    }

    /**
     * Compute the proration for one employee in the payroll month identified by {@code period}.
     *
     * @param policy      the (frozen) policy snapshot driving the computation.
     * @param period      the payroll month.
     * @param joinDate    employment start (null = employed before the month began).
     * @param leaveDate   employment end (null = still employed at month end).
     * @param unpaidDays  count of unpaid/absence days within the eligible window to deduct
     *                    (ignored when the policy does not support unpaid days). Must be &gt;= 0.
     * @throws ProrationException when the employment dates are invalid for the period.
     */
    public ProrationResult prorate(ProrationPolicySnapshot policy, YearMonth period,
                                   LocalDate joinDate, LocalDate leaveDate, int unpaidDays) {
        if (policy == null) {
            throw new ProrationException("A proration policy snapshot is required");
        }
        if (period == null) {
            throw new ProrationException("A payroll period is required");
        }
        if (unpaidDays < 0) {
            throw new ProrationException("Unpaid days cannot be negative");
        }

        LocalDate monthStart = period.atDay(1);
        LocalDate monthEnd = period.atEndOfMonth();

        if (joinDate != null && leaveDate != null && leaveDate.isBefore(joinDate)) {
            throw new ProrationException(
                    "Leaving date " + leaveDate + " is before joining date " + joinDate);
        }
        // A join after the month ends or a leave before the month starts means the employee was
        // not employed at all during the period — an invalid candidate for this run.
        if (joinDate != null && joinDate.isAfter(monthEnd)) {
            throw new ProrationException(
                    "Joining date " + joinDate + " is after the payroll period " + period);
        }
        if (leaveDate != null && leaveDate.isBefore(monthStart)) {
            throw new ProrationException(
                    "Leaving date " + leaveDate + " is before the payroll period " + period);
        }

        // Clamp the employment window to the month.
        LocalDate effectiveStart = (joinDate != null && joinDate.isAfter(monthStart)) ? joinDate : monthStart;
        LocalDate effectiveEnd = (leaveDate != null && leaveDate.isBefore(monthEnd)) ? leaveDate : monthEnd;

        boolean joinedMidMonth = joinDate != null && joinDate.isAfter(monthStart);
        boolean leftMidMonth = leaveDate != null && leaveDate.isBefore(monthEnd);

        // Honour the trigger flags: if the policy disables proration for a join/leave, treat that
        // boundary as the full month boundary so the employee is paid the full month.
        if (joinedMidMonth && !policy.isProrateOnJoin()) {
            effectiveStart = monthStart;
            joinedMidMonth = false;
        }
        if (leftMidMonth && !policy.isProrateOnLeave()) {
            effectiveEnd = monthEnd;
            leftMidMonth = false;
        }

        int totalDays = countDays(policy, monthStart, monthEnd,
                true, true);
        int eligibleDays = countDays(policy, effectiveStart, effectiveEnd,
                policy.isStartDateInclusive(), policy.isEndDateInclusive());

        if (policy.isUnpaidDaysSupported() && unpaidDays > 0) {
            eligibleDays = Math.max(0, eligibleDays - unpaidDays);
        }

        boolean fullPeriod = !joinedMidMonth && !leftMidMonth
                && (!policy.isUnpaidDaysSupported() || unpaidDays == 0);

        BigDecimal fraction;
        if (fullPeriod) {
            fraction = BigDecimal.ONE;
        } else if (eligibleDays <= 0 || totalDays <= 0) {
            fraction = BigDecimal.ZERO;
        } else {
            fraction = BigDecimal.valueOf(eligibleDays)
                    .divide(BigDecimal.valueOf(totalDays), FRACTION_CONTEXT);
        }

        return ProrationResult.builder()
                .fullPeriod(fullPeriod)
                .eligibleDays(Math.max(eligibleDays, 0))
                .totalDays(totalDays)
                .fraction(fraction)
                .build();
    }

    /**
     * Count days in {@code [start, end]} honouring the policy's day-count method and the
     * inclusivity flags. When a boundary is exclusive, that single endpoint day is dropped.
     */
    private int countDays(ProrationPolicySnapshot policy, LocalDate start, LocalDate end,
                          boolean startInclusive, boolean endInclusive) {
        if (end.isBefore(start)) {
            return 0;
        }
        LocalDate from = startInclusive ? start : start.plusDays(1);
        LocalDate to = endInclusive ? end : end.minusDays(1);
        if (to.isBefore(from)) {
            return 0;
        }
        if (policy.getDayCountMethod() == DayCountMethod.WORKING_DAYS) {
            return countWorkingDays(policy.getWorkingDaysMask(), from, to);
        }
        // Calendar days inclusive of both ends.
        return (int) (to.toEpochDay() - from.toEpochDay() + 1);
    }

    private int countWorkingDays(String mask, LocalDate from, LocalDate to) {
        String m = (mask != null && mask.length() == 7) ? mask : "YYYYYNN";
        int count = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            // DayOfWeek: MONDAY=1 .. SUNDAY=7 -> mask index 0..6.
            int idx = d.getDayOfWeek().getValue() - 1;
            if (m.charAt(idx) == 'Y' || m.charAt(idx) == 'y') {
                count++;
            }
        }
        return count;
    }

    /** Convenience for tests / callers that only need the weekday check. */
    public static boolean isWorkingDay(String mask, DayOfWeek day) {
        String m = (mask != null && mask.length() == 7) ? mask : "YYYYYNN";
        int idx = day.getValue() - 1;
        return m.charAt(idx) == 'Y' || m.charAt(idx) == 'y';
    }
}
