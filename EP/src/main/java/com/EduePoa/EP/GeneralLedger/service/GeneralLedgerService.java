package com.EduePoa.EP.GeneralLedger.service;

import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.GeneralLedger.entity.GlAccount;
import com.EduePoa.EP.GeneralLedger.entity.JournalEntry;
import com.EduePoa.EP.GeneralLedger.entity.JournalLine;
import com.EduePoa.EP.GeneralLedger.enums.GlSourceType;
import com.EduePoa.EP.GeneralLedger.enums.JournalStatus;
import com.EduePoa.EP.GeneralLedger.entity.GlPostingRequest;
import com.EduePoa.EP.GeneralLedger.enums.GlPostingStatus;
import com.EduePoa.EP.GeneralLedger.repository.GlAccountRepository;
import com.EduePoa.EP.GeneralLedger.repository.GlPostingRequestRepository;
import com.EduePoa.EP.GeneralLedger.repository.JournalEntryRepository;
import com.EduePoa.EP.GeneralLedger.service.command.PostJournalCommand;
import com.EduePoa.EP.GeneralLedger.service.command.PostJournalLine;
import com.EduePoa.EP.Multitenancy.config.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * Core double-entry posting and reversal service.
 * <p>
 * Guarantees:
 * <ul>
 *   <li><b>Balanced:</b> every journal has ≥2 lines, positive total, non-negative
 *       amounts, exactly one positive side per line, and equal total debits/credits.</li>
 *   <li><b>Tenant-safe:</b> accounts are resolved by {@code systemKey} within the
 *       current tenant; a caller can never inject a foreign tenant's account id.</li>
 *   <li><b>Idempotent:</b> a repeat {@code (sourceType, sourceId)} returns the
 *       existing journal; a concurrent duplicate is caught by the DB unique
 *       constraint and resolved to the original.</li>
 *   <li><b>Atomic:</b> header and all lines commit together (JPA cascade in one tx).</li>
 *   <li><b>Append-only:</b> posted journals/lines are never mutated; a reversal is
 *       a new counter-journal.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeneralLedgerService {

    private final JournalEntryRepository journalRepository;
    private final GlAccountRepository accountRepository;
    private final GlPostingRequestRepository postingRequestRepository;

    /** Minor-unit scale for all GL amounts. */
    private static final int SCALE = 2;

    // ---------------------------------------------------------------- posting

    /**
     * Posts a balanced journal, or returns the existing journal if this source
     * event was already posted (idempotent). Runs in the caller's transaction.
     */
    @Transactional
    public JournalEntry post(PostJournalCommand command) {
        validateCommand(command);

        // Idempotency fast-path: already posted for this source event?
        JournalEntry existing = journalRepository
                .findBySourceTypeAndSourceId(command.sourceType(), command.sourceId())
                .orElse(null);
        if (existing != null) {
            log.debug("GL journal already exists for {}#{}; returning existing {}",
                    command.sourceType(), command.sourceId(), existing.getEntryNumber());
            return existing;
        }

        JournalEntry journal = buildJournal(command);
        try {
            return journalRepository.saveAndFlush(journal);
        } catch (DataIntegrityViolationException dive) {
            // A concurrent request won the race on the (tenant, source_type, source_id)
            // unique constraint. Resolve to the winner rather than failing/duplicating.
            JournalEntry winner = journalRepository
                    .findBySourceTypeAndSourceId(command.sourceType(), command.sourceId())
                    .orElse(null);
            if (winner != null) {
                log.info("Concurrent GL post for {}#{} resolved to existing {}",
                        command.sourceType(), command.sourceId(), winner.getEntryNumber());
                return winner;
            }
            throw dive;
        }
    }

    private JournalEntry buildJournal(PostJournalCommand command) {
        JournalEntry journal = JournalEntry.builder()
                .entryNumber(nextEntryNumber())
                .accountingDate(command.accountingDate() != null ? command.accountingDate() : LocalDate.now())
                .sourceType(command.sourceType())
                .sourceId(command.sourceId())
                .referenceNumber(command.referenceNumber())
                .description(command.description())
                .status(JournalStatus.POSTED)
                .createdBy(command.actor())
                .createdByLabel(command.actorLabel())
                .lines(new ArrayList<>())
                .build();

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (PostJournalLine req : command.lines()) {
            GlAccount account = resolveAccount(req.accountSystemKey());
            if (!account.isActive()) {
                throw new GlException("Account " + account.getCode() + " (" + account.getName()
                        + ") is inactive and cannot be posted to");
            }
            BigDecimal debit = normalize(req.debit());
            BigDecimal credit = normalize(req.credit());

            JournalLine line = JournalLine.builder()
                    .account(account)
                    .debit(debit)
                    .credit(credit)
                    .narration(req.narration())
                    .build();
            journal.addLine(line);

            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new GlException("Journal does not balance: debits " + totalDebit
                    + " != credits " + totalCredit);
        }
        if (totalDebit.signum() <= 0) {
            throw new GlException("Journal must have a positive total value");
        }
        return journal;
    }

    // ------------------------------------------------------------- validation

    private void validateCommand(PostJournalCommand command) {
        if (command == null) {
            throw new GlException("Posting command is required");
        }
        if (command.sourceType() == null) {
            throw new GlException("Journal source type is required");
        }
        if (command.sourceType() != GlSourceType.REVERSAL && command.sourceId() == null) {
            throw new GlException("Journal source id is required for " + command.sourceType());
        }
        List<PostJournalLine> lines = command.lines();
        if (lines == null || lines.size() < 2) {
            throw new GlException("A journal must have at least two lines");
        }
        for (PostJournalLine line : lines) {
            if (line.accountSystemKey() == null || line.accountSystemKey().isBlank()) {
                throw new GlException("Each journal line must reference an account");
            }
            BigDecimal debit = normalize(line.debit());
            BigDecimal credit = normalize(line.credit());
            if (debit.signum() < 0 || credit.signum() < 0) {
                throw new GlException("Journal line amounts cannot be negative");
            }
            boolean debitPositive = debit.signum() > 0;
            boolean creditPositive = credit.signum() > 0;
            if (debitPositive == creditPositive) {
                // both zero, or both positive
                throw new GlException("Each journal line must have exactly one positive debit or credit");
            }
        }
    }

    // -------------------------------------------------------------- reversal

    /**
     * Creates and persists a balanced counter-journal that swaps every debit and
     * credit of {@code original}, marks the original {@link JournalStatus#REVERSED}
     * and links the two. Idempotent per original (DB unique on
     * {@code reversal_of_journal_id}) so concurrent approvals create only one
     * counter-journal. Runs in the caller's transaction.
     */
    @Transactional
    public JournalEntry reverse(JournalEntry original, String reason, User actor, String actorLabel) {
        if (original == null) {
            throw new GlException("Original journal is required");
        }
        if (original.getStatus() == JournalStatus.REVERSED) {
            throw new GlException("Journal " + original.getEntryNumber() + " is already reversed");
        }
        if (journalRepository.existsByReversalOfJournalId(original.getId())) {
            throw new GlException("Journal " + original.getEntryNumber() + " already has a reversal");
        }

        JournalEntry counter = JournalEntry.builder()
                .entryNumber(nextEntryNumber())
                .accountingDate(LocalDate.now())
                .sourceType(GlSourceType.REVERSAL)
                .sourceId(null)
                .reversalOfJournalId(original.getId())
                .referenceNumber(original.getEntryNumber())
                .description("Reversal of " + original.getEntryNumber()
                        + (reason != null ? ": " + reason : ""))
                .status(JournalStatus.POSTED)
                .createdBy(actor)
                .createdByLabel(actorLabel)
                .lines(new ArrayList<>())
                .build();

        for (JournalLine line : original.getLines()) {
            JournalLine swapped = JournalLine.builder()
                    .account(line.getAccount())
                    // swap sides
                    .debit(normalize(line.getCredit()))
                    .credit(normalize(line.getDebit()))
                    .narration("Reversal: " + (line.getNarration() != null ? line.getNarration() : ""))
                    .build();
            counter.addLine(swapped);
        }

        JournalEntry savedCounter;
        try {
            savedCounter = journalRepository.saveAndFlush(counter);
        } catch (DataIntegrityViolationException dive) {
            JournalEntry winner = journalRepository.findByReversalOfJournalId(original.getId()).orElse(null);
            if (winner != null) {
                throw new GlException("Journal " + original.getEntryNumber() + " already has a reversal");
            }
            throw dive;
        }

        original.setStatus(JournalStatus.REVERSED);
        original.setReversedByJournalId(savedCounter.getId());
        journalRepository.save(original);
        return savedCounter;
    }

    /**
     * Unwinds the GL effect of a source business event that is being deleted (e.g. an
     * approved expense). Runs in the caller's transaction and is idempotent + safe:
     * <ul>
     *   <li>If the posting is still queued in the outbox and has not produced a
     *       journal yet, the request is cancelled (status FAILED, noted) so it will
     *       not post after the source row is gone.</li>
     *   <li>If a journal was already posted, it is reversed with a balanced
     *       counter-journal (the original stays in history, never hard-deleted),
     *       keeping the ledger auditable and balanced.</li>
     *   <li>If there is nothing to do (no request, no journal, or already reversed),
     *       it is a no-op.</li>
     * </ul>
     * Never throws for "nothing to reverse"; only genuine posting errors propagate.
     */
    @Transactional
    public void reverseBySource(GlSourceType sourceType, Long sourceId, String reason,
                                User actor, String actorLabel) {
        if (sourceType == null || sourceId == null) {
            return;
        }

        // Cancel an un-posted outbox request so it cannot post after the source is deleted.
        postingRequestRepository.findBySourceTypeAndSourceId(sourceType, sourceId)
                .filter(r -> r.getStatus() != GlPostingStatus.POSTED)
                .ifPresent(r -> {
                    r.setStatus(GlPostingStatus.FAILED);
                    r.setLastError("Cancelled: source " + sourceType + "#" + sourceId
                            + " was deleted before posting" + (reason != null ? " (" + reason + ")" : ""));
                    postingRequestRepository.save(r);
                });

        // Reverse the posted journal, if one exists and is not already reversed.
        JournalEntry journal = journalRepository.findBySourceTypeAndSourceId(sourceType, sourceId).orElse(null);
        if (journal == null || journal.getStatus() == JournalStatus.REVERSED) {
            return;
        }
        if (journalRepository.existsByReversalOfJournalId(journal.getId())) {
            return; // already reversed
        }
        reverse(journal, reason, actor, actorLabel);
    }

    // ------------------------------------------------------- account resolution

    /**
     * Resolves a GL account by its {@code systemKey} within the current tenant.
     * Because the Hibernate tenant filter is active, this can only ever return an
     * account belonging to the caller's tenant — a foreign-tenant key resolves to
     * "not configured".
     */
    public GlAccount resolveAccount(String systemKey) {
        return accountRepository.findBySystemKey(systemKey)
                .orElseThrow(() -> new GlException(
                        "GL account not configured for key '" + systemKey + "' in tenant "
                                + TenantContext.getCurrentTenant()));
    }

    // -------------------------------------------------------------- numbering

    /**
     * Generates the next tenant-scoped entry number. Uniqueness is ultimately
     * guaranteed by the {@code (tenant_id, entry_number)} DB constraint; on a rare
     * collision the DB rejects the insert and the caller retries. The number
     * embeds the year and the row id for readability and monotonicity.
     */
    private String nextEntryNumber() {
        long next = journalRepository.maxId() + 1;
        return "JNL-" + Year.now().getValue() + "-" + String.format("%08d", next);
    }

    private static BigDecimal normalize(BigDecimal v) {
        if (v == null) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return v.setScale(SCALE, java.math.RoundingMode.UNNECESSARY);
    }
}
