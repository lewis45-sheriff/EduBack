package com.EduePoa.EP.HR.payroll.enums;

/**
 * Lifecycle of a payroll run. Transitions are enforced on the backend:
 * <pre>
 *   DRAFT --process--&gt; PROCESSED --approve--&gt; APPROVED --mark paid--&gt; PAID
 *   PROCESSED --reject--&gt; DRAFT
 * </pre>
 * A DRAFT may be (re)processed repeatedly. PROCESSED may be reprocessed (recompute) or approved.
 * APPROVED and PAID are frozen: their computed payslips and config/proration snapshots are never
 * recalculated or edited.
 */
public enum PayrollStatus {
    DRAFT,
    PROCESSED,
    APPROVED,
    PAID
}
