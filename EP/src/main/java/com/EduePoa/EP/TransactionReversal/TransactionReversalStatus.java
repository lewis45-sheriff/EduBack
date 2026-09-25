package com.EduePoa.EP.TransactionReversal;

/**
 * Lifecycle of a finance-transaction reversal request.
 * <p>
 * A reversal is created as {@link #PENDING_APPROVAL} and has NO financial effect
 * until a different user approves it ({@link #APPROVED}). Approval is the only
 * point at which the original transaction is undone and invoices/finance balances
 * are restored. A rejected request ({@link #REJECTED}) never changes anything.
 */
public enum TransactionReversalStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}
