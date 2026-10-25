package com.EduePoa.EP.GeneralLedger.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record JournalLineDTO(
        Long id,
        Long accountId,
        String accountCode,
        String accountName,
        BigDecimal debit,
        BigDecimal credit,
        String narration,
        int lineOrder
) {
}
