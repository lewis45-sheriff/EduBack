package com.EduePoa.EP.GeneralLedger.enums;

/**
 * Lifecycle of a durable GL posting request (the transactional-outbox record).
 * <p>
 * A request is written {@link #PENDING} in the same database transaction as the
 * finalized money event, processed into a journal to become {@link #POSTED}, or
 * recorded {@link #FAILED} with the last error for diagnosis and safe retry. A
 * failure is never silently discarded.
 */
public enum GlPostingStatus {
    PENDING,
    POSTED,
    FAILED
}
