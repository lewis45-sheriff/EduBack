package com.EduePoa.EP.PaymentTransfer;

/**
 * Lifecycle of a student-to-student payment transfer request.
 * <p>
 * A transfer is created as {@link #PENDING_APPROVAL} and has NO financial
 * effect until a different user approves it ({@link #APPROVED}). Approval is the
 * only point at which invoices and finance balances are touched. A rejected
 * request ({@link #REJECTED}) never moves any money.
 */
public enum PaymentTransferStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    /** An APPROVED transfer whose effect was later undone by a transaction reversal. */
    REVERSED
}
