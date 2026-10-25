package com.EduePoa.EP.GeneralLedger.repository.projection;

import java.math.BigDecimal;

/** Spring Data projection: posted debit/credit totals for one account. */
public interface AccountBalanceRow {
    Long getAccountId();
    BigDecimal getTotalDebit();
    BigDecimal getTotalCredit();
}
