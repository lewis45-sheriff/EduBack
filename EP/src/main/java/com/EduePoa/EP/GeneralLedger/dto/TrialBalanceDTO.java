package com.EduePoa.EP.GeneralLedger.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Trial balance as of a date. {@code balanced} is true only when total debit
 * balances equal total credit balances; the frontend must not show a success
 * state when this is false.
 */
@Builder
public record TrialBalanceDTO(
        LocalDate asOf,
        List<Row> rows,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        boolean balanced
) {
    @Builder
    public record Row(
            Long accountId,
            String code,
            String name,
            String accountType,
            BigDecimal debitBalance,
            BigDecimal creditBalance
    ) {
    }
}
