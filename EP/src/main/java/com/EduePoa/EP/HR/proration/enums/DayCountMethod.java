package com.EduePoa.EP.HR.proration.enums;

/**
 * How days are counted when prorating a partial month.
 */
public enum DayCountMethod {
    /** Every calendar day in the month counts (proration denominator = days in month). */
    CALENDAR_DAYS,
    /** Only working days count (denominator = working days in the month, per the policy's work-week). */
    WORKING_DAYS
}
