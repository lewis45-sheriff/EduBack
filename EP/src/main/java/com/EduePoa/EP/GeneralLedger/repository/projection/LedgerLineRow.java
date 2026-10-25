package com.EduePoa.EP.GeneralLedger.repository.projection;

import com.EduePoa.EP.GeneralLedger.enums.GlSourceType;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Spring Data projection: one movement row of an account statement. */
public interface LedgerLineRow {
    Long getJournalId();
    String getEntryNumber();
    LocalDate getAccountingDate();
    GlSourceType getSourceType();
    String getReferenceNumber();
    String getNarration();
    BigDecimal getDebit();
    BigDecimal getCredit();
}
