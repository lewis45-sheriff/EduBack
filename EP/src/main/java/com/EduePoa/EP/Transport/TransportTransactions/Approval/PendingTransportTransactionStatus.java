package com.EduePoa.EP.Transport.TransportTransactions.Approval;

/**
 * Lifecycle of a transport fee payment request under maker-checker control.
 *
 * <p>A request is created as {@link #PENDING_APPROVAL} and has NO effect on the transport ledger
 * ({@code transport_transactions}) until a DIFFERENT user approves it ({@link #APPROVED}). Approval
 * is the only point at which the real {@code TransportTransactions} row is written and the student's
 * running balance changes. A {@link #REJECTED} request never changes anything.
 */
public enum PendingTransportTransactionStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}
