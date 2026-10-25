package com.EduePoa.EP.GeneralLedger.enums;

/**
 * Maker-checker lifecycle of a journal reversal request, mirroring the existing
 * {@code TransactionReversalStatus} convention.
 */
public enum GlReversalStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}
