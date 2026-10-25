package com.EduePoa.EP.GeneralLedger.enums;

/**
 * Lifecycle of a journal entry. Journals are append-only: a {@link #POSTED}
 * journal is never edited or deleted. When it is reversed it transitions to
 * {@link #REVERSED} and a separate counter-journal is created; both remain
 * visible in history and statements.
 */
public enum JournalStatus {
    POSTED,
    REVERSED
}
