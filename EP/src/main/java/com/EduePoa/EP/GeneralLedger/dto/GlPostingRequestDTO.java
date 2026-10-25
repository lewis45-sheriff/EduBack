package com.EduePoa.EP.GeneralLedger.dto;

import com.EduePoa.EP.GeneralLedger.enums.GlPostingStatus;
import com.EduePoa.EP.GeneralLedger.enums.GlSourceType;
import lombok.Builder;

import java.time.LocalDateTime;

/** A durable outbox posting request, for the reconciliation/retry screen. */
@Builder
public record GlPostingRequestDTO(
        Long id,
        GlSourceType sourceType,
        Long sourceId,
        GlPostingStatus status,
        int attempts,
        String lastError,
        Long journalId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime postedAt
) {
}
