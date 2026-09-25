package com.EduePoa.EP.FinanceTransaction.PendingTransaction;

/**
 * Lifecycle of a manually-entered finance transaction awaiting approval.
 * Only used when the maker-checker flag is enabled; a pending transaction has
 * NO effect on invoices/finance until a different user approves it.
 */
public enum PendingTransactionStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}
