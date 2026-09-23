package com.EduePoa.EP.Budget.Enum;

/**
 * Budget lifecycle. Valid transitions: DRAFT -> APPROVED -> CLOSED.
 * Changes to an APPROVED budget go through the amendment workflow (Phase 4).
 */
public enum BudgetStatus {
    DRAFT,
    APPROVED,
    CLOSED
}
