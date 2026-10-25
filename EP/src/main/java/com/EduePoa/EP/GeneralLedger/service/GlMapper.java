package com.EduePoa.EP.GeneralLedger.service;

import com.EduePoa.EP.GeneralLedger.dto.GlPostingRequestDTO;
import com.EduePoa.EP.GeneralLedger.dto.GlReversalRequestDTO;
import com.EduePoa.EP.GeneralLedger.dto.JournalEntryDTO;
import com.EduePoa.EP.GeneralLedger.dto.JournalLineDTO;
import com.EduePoa.EP.GeneralLedger.entity.GlPostingRequest;
import com.EduePoa.EP.GeneralLedger.entity.GlReversalRequest;
import com.EduePoa.EP.GeneralLedger.entity.JournalEntry;
import com.EduePoa.EP.GeneralLedger.entity.JournalLine;

import java.math.BigDecimal;

/** Null-safe entity → DTO mapping for GL read endpoints. */
public final class GlMapper {

    private GlMapper() {
    }

    public static JournalEntryDTO toJournalDto(JournalEntry j) {
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (JournalLine l : j.getLines()) {
            totalDebit = totalDebit.add(nz(l.getDebit()));
            totalCredit = totalCredit.add(nz(l.getCredit()));
        }
        return JournalEntryDTO.builder()
                .id(j.getId())
                .entryNumber(j.getEntryNumber())
                .accountingDate(j.getAccountingDate())
                .sourceType(j.getSourceType())
                .sourceId(j.getSourceId())
                .referenceNumber(j.getReferenceNumber())
                .description(j.getDescription())
                .status(j.getStatus())
                .reversalOfJournalId(j.getReversalOfJournalId())
                .reversedByJournalId(j.getReversedByJournalId())
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .createdBy(resolveActor(j))
                .createdAt(j.getCreatedAt())
                .lines(j.getLines().stream()
                        .sorted(java.util.Comparator.comparingInt(JournalLine::getLineOrder))
                        .map(GlMapper::toLineDto)
                        .toList())
                .build();
    }

    public static JournalLineDTO toLineDto(JournalLine l) {
        return JournalLineDTO.builder()
                .id(l.getId())
                .accountId(l.getAccount() != null ? l.getAccount().getId() : null)
                .accountCode(l.getAccount() != null ? l.getAccount().getCode() : null)
                .accountName(l.getAccount() != null ? l.getAccount().getName() : null)
                .debit(nz(l.getDebit()))
                .credit(nz(l.getCredit()))
                .narration(l.getNarration())
                .lineOrder(l.getLineOrder())
                .build();
    }

    public static GlReversalRequestDTO toReversalDto(GlReversalRequest r) {
        return GlReversalRequestDTO.builder()
                .id(r.getId())
                .targetJournalId(r.getTargetJournalId())
                .targetEntryNumber(r.getTargetEntryNumber())
                .targetDescription(r.getTargetDescription())
                .targetTotal(r.getTargetTotal())
                .targetSourceType(r.getTargetSourceType())
                .reason(r.getReason())
                .status(r.getStatus())
                .counterJournalId(r.getCounterJournalId())
                .createdByName(r.getCreatedBy() != null ? r.getCreatedBy().getUsername() : null)
                .createdAt(r.getCreatedAt())
                .decidedByName(r.getDecidedBy() != null ? r.getDecidedBy().getUsername() : null)
                .decidedAt(r.getDecidedAt())
                .decisionReason(r.getDecisionReason())
                .build();
    }

    public static GlPostingRequestDTO toPostingDto(GlPostingRequest p) {
        return GlPostingRequestDTO.builder()
                .id(p.getId())
                .sourceType(p.getSourceType())
                .sourceId(p.getSourceId())
                .status(p.getStatus())
                .attempts(p.getAttempts())
                .lastError(p.getLastError())
                .journalId(p.getJournalId())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .postedAt(p.getPostedAt())
                .build();
    }

    private static String resolveActor(JournalEntry j) {
        if (j.getCreatedBy() != null) {
            return j.getCreatedBy().getUsername();
        }
        return j.getCreatedByLabel();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
