package com.EduePoa.EP.GeneralLedger.dto;

import com.EduePoa.EP.GeneralLedger.enums.GlAccountType;
import com.EduePoa.EP.GeneralLedger.enums.GlNormalBalance;

/**
 * Create/update payload for an administrator-managed GL account. {@code systemKey}
 * is not accepted from the client — system keys are reserved for seeded accounts.
 */
public record GlAccountRequest(
        String code,
        String name,
        GlAccountType accountType,
        GlNormalBalance normalBalance,
        Boolean active,
        String description
) {
}
