package com.EduePoa.EP.HR.proration.enums;

/**
 * How fixed monthly allowances (those flagged {@code fixedMonthly} on the compensation) are
 * treated in a partial month.
 */
public enum FixedAllowanceProrationRule {
    /** Fixed allowances are prorated alongside basic salary. */
    PRORATE,
    /** Fixed allowances are paid in full regardless of a partial month. */
    PAY_IN_FULL
}
