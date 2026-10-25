package com.EduePoa.EP.GeneralLedger.dto;

import com.EduePoa.EP.GeneralLedger.enums.GlReversalStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record GlReversalRequestDTO(
        Long id,
        Long targetJournalId,
        String targetEntryNumber,
        String targetDescription,
        BigDecimal targetTotal,
        String targetSourceType,
        String reason,
        GlReversalStatus status,
        Long counterJournalId,
        String createdByName,
        LocalDateTime createdAt,
        String decidedByName,
        LocalDateTime decidedAt,
        String decisionReason
) {
}
