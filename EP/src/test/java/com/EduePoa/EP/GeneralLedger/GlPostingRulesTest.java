package com.EduePoa.EP.GeneralLedger;

import com.EduePoa.EP.GeneralLedger.enums.GlSourceType;
import com.EduePoa.EP.GeneralLedger.service.GlException;
import com.EduePoa.EP.GeneralLedger.service.command.PostJournalCommand;
import com.EduePoa.EP.GeneralLedger.service.command.PostJournalLine;
import com.EduePoa.EP.GeneralLedger.service.posting.GlPostingPayload;
import com.EduePoa.EP.GeneralLedger.service.posting.GlPostingRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for the source-specific posting rules. They assert that each rule produces a
 * balanced command (debits == credits), targets the correct accounts, and that the payroll rule
 * rejects a run whose totals do not reconcile rather than plugging the difference.
 */
class GlPostingRulesTest {

    private final GlPostingRules rules = new GlPostingRules();

    private static BigDecimal totalDebit(PostJournalCommand c) {
        return c.lines().stream().map(PostJournalLine::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal totalCredit(PostJournalCommand c) {
        return c.lines().stream().map(PostJournalLine::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean hasLine(PostJournalCommand c, String key, boolean debit, String amount) {
        return c.lines().stream().anyMatch(l ->
                l.accountSystemKey().equals(key)
                        && (debit ? l.debit() : l.credit()).compareTo(new BigDecimal(amount)) == 0
                        && (debit ? l.credit() : l.debit()).signum() == 0);
    }

    private GlPostingPayload simple(String amount) {
        return new GlPostingPayload(LocalDate.now(), "REF-1", "desc",
                Map.of("amount", new BigDecimal(amount)));
    }

    @Test
    @DisplayName("Fee receipt debits Cash/Bank and credits Fees Income, balanced")
    void feeReceipt() {
        PostJournalCommand c = rules.build(GlSourceType.STUDENT_FEE_RECEIPT, 1L, simple("5000.00"), null, "sys");
        assertThat(totalDebit(c)).isEqualByComparingTo("5000.00");
        assertThat(totalCredit(c)).isEqualByComparingTo("5000.00");
        assertThat(hasLine(c, GlAccountKeys.CASH_BANK, true, "5000.00")).isTrue();
        assertThat(hasLine(c, GlAccountKeys.FEES_INCOME, false, "5000.00")).isTrue();
    }

    @Test
    @DisplayName("Fee refund reverses income direction (Dr Fees Income / Cr Cash)")
    void feeRefund() {
        PostJournalCommand c = rules.build(GlSourceType.STUDENT_FEE_REFUND, 2L, simple("1200.00"), null, "sys");
        assertThat(hasLine(c, GlAccountKeys.FEES_INCOME, true, "1200.00")).isTrue();
        assertThat(hasLine(c, GlAccountKeys.CASH_BANK, false, "1200.00")).isTrue();
    }

    @Test
    @DisplayName("Transport receipt credits Transport income, not fees income")
    void transportReceipt() {
        PostJournalCommand c = rules.build(GlSourceType.TRANSPORT_FEE_RECEIPT, 3L, simple("800.00"), null, "sys");
        assertThat(hasLine(c, GlAccountKeys.CASH_BANK, true, "800.00")).isTrue();
        assertThat(hasLine(c, GlAccountKeys.TRANSPORT_INCOME, false, "800.00")).isTrue();
        assertThat(c.lines()).noneMatch(l -> l.accountSystemKey().equals(GlAccountKeys.FEES_INCOME));
    }

    @Test
    @DisplayName("Supplier payment settles AP against cash (Dr AP / Cr Cash)")
    void supplierPayment() {
        PostJournalCommand c = rules.build(GlSourceType.SUPPLIER_PAYMENT, 4L, simple("9000.00"), null, "sys");
        assertThat(hasLine(c, GlAccountKeys.SUPPLIER_AP, true, "9000.00")).isTrue();
        assertThat(hasLine(c, GlAccountKeys.CASH_BANK, false, "9000.00")).isTrue();
        assertThat(c.lines()).noneMatch(l -> l.accountSystemKey().equals(GlAccountKeys.EXPENSE_GENERAL));
    }

    @Test
    @DisplayName("Expense debits General Expenses and credits cash")
    void expense() {
        PostJournalCommand c = rules.build(GlSourceType.EXPENSE_PAYMENT, 5L, simple("450.00"), null, "sys");
        assertThat(hasLine(c, GlAccountKeys.EXPENSE_GENERAL, true, "450.00")).isTrue();
        assertThat(hasLine(c, GlAccountKeys.CASH_BANK, false, "450.00")).isTrue();
    }

    @Test
    @DisplayName("A non-positive amount is rejected")
    void nonPositiveRejected() {
        assertThatThrownBy(() -> rules.build(GlSourceType.STUDENT_FEE_RECEIPT, 6L, simple("0.00"), null, "sys"))
                .isInstanceOf(GlException.class);
    }

    @Test
    @DisplayName("Payroll run builds a balanced accrual from reconciled totals")
    void payrollBalanced() {
        // gross 100000; employer contributions 10000; net 71617.65; paye 19812.35;
        // nssf(ee+er) 8640; shif 2750; ahl(ee+er) 3000; nita 0; other 0
        // Debit = 100000 + 10000 = 110000; Credit = 71617.65 + 19812.35 + 8640 + 2750 + 3000 + 4180 = 110000
        Map<String, BigDecimal> m = new HashMap<>();
        m.put("totalGross", new BigDecimal("100000.00"));
        m.put("totalEmployerContributions", new BigDecimal("10000.00"));
        m.put("totalNet", new BigDecimal("71617.65"));
        m.put("totalPaye", new BigDecimal("19812.35"));
        m.put("totalNssf", new BigDecimal("8640.00"));
        m.put("totalShif", new BigDecimal("2750.00"));
        m.put("totalAhl", new BigDecimal("3000.00"));
        m.put("totalNita", new BigDecimal("0.00"));
        m.put("totalOtherDeductions", new BigDecimal("4180.00"));
        GlPostingPayload payload = new GlPostingPayload(LocalDate.now(), "PR-1", "payroll", m);

        PostJournalCommand c = rules.build(GlSourceType.PAYROLL_RUN, 7L, payload, null, "sys");
        assertThat(totalDebit(c)).isEqualByComparingTo("110000.00");
        assertThat(totalCredit(c)).isEqualByComparingTo("110000.00");
        assertThat(hasLine(c, GlAccountKeys.SALARIES_EXPENSE, true, "100000.00")).isTrue();
        assertThat(hasLine(c, GlAccountKeys.EMPLOYER_STATUTORY_EXPENSE, true, "10000.00")).isTrue();
        assertThat(hasLine(c, GlAccountKeys.NET_PAY_PAYABLE, false, "71617.65")).isTrue();
    }

    @Test
    @DisplayName("Payroll that does not reconcile is rejected, never plugged")
    void payrollImbalanceRejected() {
        Map<String, BigDecimal> m = new HashMap<>();
        m.put("totalGross", new BigDecimal("100000.00"));
        m.put("totalEmployerContributions", new BigDecimal("10000.00"));
        m.put("totalNet", new BigDecimal("70000.00")); // wrong — credits won't equal debits
        m.put("totalPaye", new BigDecimal("19812.35"));
        m.put("totalNssf", new BigDecimal("8640.00"));
        m.put("totalShif", new BigDecimal("2750.00"));
        m.put("totalAhl", new BigDecimal("3000.00"));
        m.put("totalNita", new BigDecimal("0.00"));
        m.put("totalOtherDeductions", new BigDecimal("0.00"));
        GlPostingPayload payload = new GlPostingPayload(LocalDate.now(), "PR-2", "payroll", m);

        assertThatThrownBy(() -> rules.build(GlSourceType.PAYROLL_RUN, 8L, payload, null, "sys"))
                .isInstanceOf(GlException.class)
                .hasMessageContaining("does not reconcile");
    }

    @Test
    @DisplayName("Rules never auto-build MANUAL or REVERSAL journals")
    void manualAndReversalRejected() {
        assertThatThrownBy(() -> rules.build(GlSourceType.MANUAL, 9L, simple("1.00"), null, "sys"))
                .isInstanceOf(GlException.class);
        assertThatThrownBy(() -> rules.build(GlSourceType.REVERSAL, 9L, simple("1.00"), null, "sys"))
                .isInstanceOf(GlException.class);
    }
}
