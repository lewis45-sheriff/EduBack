package com.EduePoa.EP.HR.proration;

import com.EduePoa.EP.HR.engine.PayrollTestFixtures;
import com.EduePoa.EP.HR.proration.enums.DayCountMethod;
import com.EduePoa.EP.HR.proration.service.ProrationException;
import com.EduePoa.EP.HR.proration.service.ProrationPolicySnapshot;
import com.EduePoa.EP.HR.proration.service.ProrationResult;
import com.EduePoa.EP.HR.proration.service.ProrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the proration day-counting engine, covering first/middle/last-day joiners,
 * mid-month leavers, months of different lengths, zero-eligible and invalid-date cases, unpaid
 * days, the trigger flags, and working-day counting.
 */
class ProrationServiceTest {

    // The pure prorate(...) path does not touch the repository; pass null since these tests never
    // call resolveSnapshot(...).
    private final ProrationService service = new ProrationService(null);
    private final ProrationPolicySnapshot policy = PayrollTestFixtures.calendarPolicy();

    private final YearMonth july = YearMonth.of(2025, 7);   // 31 days
    private final YearMonth february = YearMonth.of(2025, 2); // 28 days (non-leap)

    @Test
    @DisplayName("Employed for the whole month => full period, fraction 1")
    void fullMonth() {
        ProrationResult r = service.prorate(policy, july,
                LocalDate.of(2020, 1, 1), null, 0);
        assertThat(r.isFullPeriod()).isTrue();
        assertThat(r.getFraction()).isEqualByComparingTo("1");
        assertThat(r.getEligibleDays()).isEqualTo(31);
    }

    @Test
    @DisplayName("Joining on the first day of the month is still a full period")
    void joinFirstDay() {
        ProrationResult r = service.prorate(policy, july,
                LocalDate.of(2025, 7, 1), null, 0);
        assertThat(r.isFullPeriod()).isTrue();
        assertThat(r.getEligibleDays()).isEqualTo(31);
    }

    @Test
    @DisplayName("Joining mid-month (16th, inclusive) yields 16 of 31 eligible days")
    void joinMidMonth() {
        ProrationResult r = service.prorate(policy, july,
                LocalDate.of(2025, 7, 16), null, 0);
        assertThat(r.isFullPeriod()).isFalse();
        assertThat(r.getEligibleDays()).isEqualTo(16); // 16..31 inclusive
        assertThat(r.getTotalDays()).isEqualTo(31);
    }

    @Test
    @DisplayName("Joining on the final day yields a single eligible day")
    void joinLastDay() {
        ProrationResult r = service.prorate(policy, july,
                LocalDate.of(2025, 7, 31), null, 0);
        assertThat(r.getEligibleDays()).isEqualTo(1);
        assertThat(r.getTotalDays()).isEqualTo(31);
    }

    @Test
    @DisplayName("Leaving mid-month (15th, inclusive) yields 15 of 31 eligible days")
    void leaveMidMonth() {
        ProrationResult r = service.prorate(policy, july,
                null, LocalDate.of(2025, 7, 15), 0);
        assertThat(r.isFullPeriod()).isFalse();
        assertThat(r.getEligibleDays()).isEqualTo(15);
    }

    @Test
    @DisplayName("Different month length: February uses 28 as the denominator")
    void februaryDenominator() {
        ProrationResult r = service.prorate(policy, february,
                LocalDate.of(2025, 2, 15), null, 0);
        assertThat(r.getTotalDays()).isEqualTo(28);
        assertThat(r.getEligibleDays()).isEqualTo(14); // 15..28 inclusive
    }

    @Test
    @DisplayName("Unpaid days reduce eligible days")
    void unpaidDays() {
        ProrationResult r = service.prorate(policy, july,
                null, null, 5);
        assertThat(r.isFullPeriod()).isFalse();
        assertThat(r.getEligibleDays()).isEqualTo(26); // 31 - 5
        assertThat(r.getTotalDays()).isEqualTo(31);
    }

    @Test
    @DisplayName("Zero eligible days when unpaid days consume the whole eligible window")
    void zeroEligible() {
        ProrationResult r = service.prorate(policy, july,
                LocalDate.of(2025, 7, 29), null, 10); // 3 eligible - 10 unpaid => floored 0
        assertThat(r.isZeroEligible()).isTrue();
        assertThat(r.getFraction()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Leaving before joining is an invalid employment period")
    void leaveBeforeJoinInvalid() {
        assertThatThrownBy(() -> service.prorate(policy, july,
                LocalDate.of(2025, 7, 20), LocalDate.of(2025, 7, 10), 0))
                .isInstanceOf(ProrationException.class);
    }

    @Test
    @DisplayName("Joining after the period end is invalid for that run")
    void joinAfterPeriodInvalid() {
        assertThatThrownBy(() -> service.prorate(policy, july,
                LocalDate.of(2025, 8, 1), null, 0))
                .isInstanceOf(ProrationException.class);
    }

    @Test
    @DisplayName("Leaving before the period start is invalid for that run")
    void leaveBeforePeriodInvalid() {
        assertThatThrownBy(() -> service.prorate(policy, july,
                null, LocalDate.of(2025, 6, 30), 0))
                .isInstanceOf(ProrationException.class);
    }

    @Test
    @DisplayName("Exclusive start date drops the joining day from the eligible count")
    void exclusiveStart() {
        ProrationPolicySnapshot exclusive = PayrollTestFixtures.calendarPolicy();
        exclusive.setStartDateInclusive(false);
        ProrationResult r = service.prorate(exclusive, july,
                LocalDate.of(2025, 7, 16), null, 0);
        assertThat(r.getEligibleDays()).isEqualTo(15); // 17..31
    }

    @Test
    @DisplayName("Disabling prorate-on-join pays a mid-month joiner the full month")
    void triggerDisablesJoinProration() {
        ProrationPolicySnapshot noJoinProration = PayrollTestFixtures.calendarPolicy();
        noJoinProration.setProrateOnJoin(false);
        ProrationResult r = service.prorate(noJoinProration, july,
                LocalDate.of(2025, 7, 16), null, 0);
        assertThat(r.isFullPeriod()).isTrue();
        assertThat(r.getEligibleDays()).isEqualTo(31);
    }

    @Test
    @DisplayName("Working-day counting excludes weekends from both the eligible days and the denominator")
    void workingDays() {
        ProrationPolicySnapshot workingDayPolicy = PayrollTestFixtures.calendarPolicy();
        workingDayPolicy.setDayCountMethod(DayCountMethod.WORKING_DAYS);
        workingDayPolicy.setWorkingDaysMask("YYYYYNN"); // Mon-Fri
        // July 2025: 23 weekdays. Starting 2025-07-16 (a Wednesday) through 31st.
        ProrationResult r = service.prorate(workingDayPolicy, july,
                LocalDate.of(2025, 7, 16), null, 0);
        assertThat(r.getTotalDays()).isEqualTo(23);
        assertThat(r.getEligibleDays()).isGreaterThan(0).isLessThan(23);
    }
}
