package com.EduePoa.EP.GeneralLedger.dto;

import com.EduePoa.EP.GeneralLedger.enums.GlSourceType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Account statement over a date range: an opening balance that accounts for ALL
 * prior activity (not just the shown rows), the ordered movements with a running
 * balance, and the closing balance.
 */
@Builder
public record AccountStatementDTO(
        Long accountId,
        String code,
        String name,
        LocalDate from,
        LocalDate to,
        BigDecimal openingBalance,
        List<Movement> movements,
        BigDecimal closingBalance
) {
    @Builder
    public record Movement(
            Long journalId,
            String entryNumber,
            LocalDate accountingDate,
            GlSourceType sourceType,
            String referenceNumber,
            String narration,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal runningBalance
    ) {
    }
}
