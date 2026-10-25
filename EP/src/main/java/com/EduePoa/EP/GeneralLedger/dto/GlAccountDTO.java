package com.EduePoa.EP.GeneralLedger.dto;

import com.EduePoa.EP.GeneralLedger.enums.GlAccountType;
import com.EduePoa.EP.GeneralLedger.enums.GlNormalBalance;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * Chart-of-accounts row with its derived balance. {@code signedBalance} is
 * debits minus credits; {@code normalBalance} presents it positive for the
 * account's natural side.
 */
@Builder
public record GlAccountDTO(
        Long id,
        String code,
        String name,
        GlAccountType accountType,
        GlNormalBalance normalBalance,
        String systemKey,
        boolean active,
        boolean systemAccount,
        String description,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        BigDecimal signedBalance,
        BigDecimal normalBalanceAmount
) {
}
