package com.EduePoa.EP.GeneralLedger.service.command;

import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.GeneralLedger.enums.GlSourceType;

import java.time.LocalDate;
import java.util.List;

/**
 * An instruction to post one balanced journal. Lines reference accounts by
 * {@code systemKey} (resolved within the current tenant). Idempotency is keyed on
 * {@code (sourceType, sourceId)}; a repeat command with the same key returns the
 * existing journal instead of creating a duplicate.
 */
public record PostJournalCommand(
        GlSourceType sourceType,
        Long sourceId,
        LocalDate accountingDate,
        String referenceNumber,
        String description,
        List<PostJournalLine> lines,
        User actor,
        String actorLabel
) {
}
