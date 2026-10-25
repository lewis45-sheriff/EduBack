package com.EduePoa.EP.GeneralLedger.dto;

import com.EduePoa.EP.GeneralLedger.enums.GlSourceType;
import com.EduePoa.EP.GeneralLedger.enums.JournalStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record JournalEntryDTO(
        Long id,
        String entryNumber,
        LocalDate accountingDate,
        GlSourceType sourceType,
        Long sourceId,
        String referenceNumber,
        String description,
        JournalStatus status,
        Long reversalOfJournalId,
        Long reversedByJournalId,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        String createdBy,
        LocalDateTime createdAt,
        List<JournalLineDTO> lines
) {
}
