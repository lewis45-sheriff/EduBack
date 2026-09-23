package com.EduePoa.EP.Budget.Enum;

/**
 * Performance classification of a budget line's actual against its allocation.
 * Expense lines use UNDER_BUDGET / AT_BUDGET / OVER_BUDGET.
 * Income lines use BELOW_TARGET / AT_TARGET / ABOVE_TARGET.
 */
public enum PerformanceStatus {
    // Expense semantics
    UNDER_BUDGET,
    AT_BUDGET,
    OVER_BUDGET,
    // Income semantics
    BELOW_TARGET,
    AT_TARGET,
    ABOVE_TARGET
}
