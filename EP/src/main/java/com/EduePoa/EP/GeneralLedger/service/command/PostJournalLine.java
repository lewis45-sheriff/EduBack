package com.EduePoa.EP.GeneralLedger.service.command;

import java.math.BigDecimal;

/**
 * One requested journal line, resolved by the stable {@code systemKey} of a GL
 * account so callers never pass raw (and potentially cross-tenant) account ids.
 * Exactly one of {@code debit}/{@code credit} must be positive.
 */
public record PostJournalLine(
        String accountSystemKey,
        BigDecimal debit,
        BigDecimal credit,
        String narration
) {
    public static PostJournalLine debit(String systemKey, BigDecimal amount, String narration) {
        return new PostJournalLine(systemKey, amount, BigDecimal.ZERO, narration);
    }

    public static PostJournalLine credit(String systemKey, BigDecimal amount, String narration) {
        return new PostJournalLine(systemKey, BigDecimal.ZERO, amount, narration);
    }
}
