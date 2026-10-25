package com.EduePoa.EP.GeneralLedger.enums;

import com.EduePoa.EP.GeneralLedger.enums.GlNormalBalance;

/**
 * The five fundamental account classifications in double-entry accounting.
 * <p>
 * Each type has a conventional normal balance ({@link #defaultNormalBalance()}):
 * assets and expenses are naturally debit-balanced; liabilities, equity and
 * income are naturally credit-balanced. The chart of accounts validates each
 * account's declared normal balance against this default, allowing a documented
 * exception only for genuine contra accounts.
 */
public enum GlAccountType {
    ASSET(GlNormalBalance.DEBIT),
    LIABILITY(GlNormalBalance.CREDIT),
    EQUITY(GlNormalBalance.CREDIT),
    INCOME(GlNormalBalance.CREDIT),
    EXPENSE(GlNormalBalance.DEBIT);

    private final GlNormalBalance defaultNormalBalance;

    GlAccountType(GlNormalBalance defaultNormalBalance) {
        this.defaultNormalBalance = defaultNormalBalance;
    }

    public GlNormalBalance defaultNormalBalance() {
        return defaultNormalBalance;
    }
}
