package com.EduePoa.EP.GeneralLedger.service.posting;

import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.GeneralLedger.GlAccountKeys;
import com.EduePoa.EP.GeneralLedger.enums.GlSourceType;
import com.EduePoa.EP.GeneralLedger.service.GlException;
import com.EduePoa.EP.GeneralLedger.service.command.PostJournalCommand;
import com.EduePoa.EP.GeneralLedger.service.command.PostJournalLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Translates a finalized money event (source type + payload) into a balanced
 * {@link PostJournalCommand}. This is the single place every posting rule lives,
 * so the accounting treatment of each event is auditable in one file.
 * <p>
 * Rules (cash-basis, per the approved plan):
 * <ul>
 *   <li>STUDENT_FEE_RECEIPT: Dr Cash/Bank, Cr Fees Income.</li>
 *   <li>STUDENT_FEE_REFUND: Dr Fees Income (contra), Cr Cash/Bank.</li>
 *   <li>TRANSPORT_FEE_RECEIPT: Dr Cash/Bank, Cr Transport Income.</li>
 *   <li>SUPPLIER_PAYMENT: Dr Accounts Payable (settle liability), Cr Cash/Bank.</li>
 *   <li>EXPENSE_PAYMENT: Dr General Expenses, Cr Cash/Bank.</li>
 *   <li>PAYROLL_RUN: Dr Salaries + Dr Employer Statutory, Cr Net Pay + each
 *       statutory liability + Other Deductions. Debits equal total employer cost,
 *       which equals total credits by the payroll engine's own identity.</li>
 * </ul>
 */
@Component
public class GlPostingRules {

    public PostJournalCommand build(GlSourceType sourceType, Long sourceId,
                                    GlPostingPayload payload, User actor, String actorLabel) {
        return switch (sourceType) {
            case STUDENT_FEE_RECEIPT -> feeReceipt(sourceId, payload, actor, actorLabel);
            case STUDENT_FEE_REFUND -> feeRefund(sourceId, payload, actor, actorLabel);
            case TRANSPORT_FEE_RECEIPT -> transportReceipt(sourceId, payload, actor, actorLabel);
            case SUPPLIER_PAYMENT -> supplierPayment(sourceId, payload, actor, actorLabel);
            case EXPENSE_PAYMENT -> expensePayment(sourceId, payload, actor, actorLabel);
            case PAYROLL_RUN -> payrollRun(sourceId, payload, actor, actorLabel);
            case MANUAL, REVERSAL -> throw new GlException(
                    sourceType + " journals are not produced by the automatic posting rules");
        };
    }

    private PostJournalCommand feeReceipt(Long id, GlPostingPayload p, User actor, String label) {
        BigDecimal amount = positive(p.amount("amount"), "fee receipt");
        List<PostJournalLine> lines = List.of(
                PostJournalLine.debit(GlAccountKeys.CASH_BANK, amount, "Fee receipt"),
                PostJournalLine.credit(GlAccountKeys.FEES_INCOME, amount, "School fees income")
        );
        return cmd(GlSourceType.STUDENT_FEE_RECEIPT, id, p, lines, actor, label);
    }

    private PostJournalCommand feeRefund(Long id, GlPostingPayload p, User actor, String label) {
        BigDecimal amount = positive(p.amount("amount"), "fee refund");
        // Reverse the income direction rather than booking a general expense.
        List<PostJournalLine> lines = List.of(
                PostJournalLine.debit(GlAccountKeys.FEES_INCOME, amount, "Fee refund / adjustment (contra revenue)"),
                PostJournalLine.credit(GlAccountKeys.CASH_BANK, amount, "Cash refunded to student")
        );
        return cmd(GlSourceType.STUDENT_FEE_REFUND, id, p, lines, actor, label);
    }

    private PostJournalCommand transportReceipt(Long id, GlPostingPayload p, User actor, String label) {
        BigDecimal amount = positive(p.amount("amount"), "transport receipt");
        List<PostJournalLine> lines = List.of(
                PostJournalLine.debit(GlAccountKeys.CASH_BANK, amount, "Transport fee receipt"),
                PostJournalLine.credit(GlAccountKeys.TRANSPORT_INCOME, amount, "Transport fees income")
        );
        return cmd(GlSourceType.TRANSPORT_FEE_RECEIPT, id, p, lines, actor, label);
    }

    private PostJournalCommand supplierPayment(Long id, GlPostingPayload p, User actor, String label) {
        BigDecimal amount = positive(p.amount("amount"), "supplier payment");
        List<PostJournalLine> lines = List.of(
                PostJournalLine.debit(GlAccountKeys.SUPPLIER_AP, amount, "Settle supplier accounts payable"),
                PostJournalLine.credit(GlAccountKeys.CASH_BANK, amount, "Cash paid to supplier")
        );
        return cmd(GlSourceType.SUPPLIER_PAYMENT, id, p, lines, actor, label);
    }

    private PostJournalCommand expensePayment(Long id, GlPostingPayload p, User actor, String label) {
        BigDecimal amount = positive(p.amount("amount"), "expense");
        List<PostJournalLine> lines = List.of(
                PostJournalLine.debit(GlAccountKeys.EXPENSE_GENERAL, amount, "School expense"),
                PostJournalLine.credit(GlAccountKeys.CASH_BANK, amount, "Cash paid for expense")
        );
        return cmd(GlSourceType.EXPENSE_PAYMENT, id, p, lines, actor, label);
    }

    private PostJournalCommand payrollRun(Long id, GlPostingPayload p, User actor, String label) {
        BigDecimal gross = p.amount("totalGross");
        BigDecimal employerContrib = p.amount("totalEmployerContributions");
        BigDecimal net = p.amount("totalNet");
        BigDecimal paye = p.amount("totalPaye");
        BigDecimal nssf = p.amount("totalNssf");           // employee + employer combined
        BigDecimal shif = p.amount("totalShif");
        BigDecimal ahl = p.amount("totalAhl");             // employee + employer combined
        BigDecimal nita = p.amount("totalNita");
        BigDecimal other = p.amount("totalOtherDeductions");

        if (gross.signum() <= 0) {
            throw new GlException("Payroll run has no gross pay to post");
        }

        List<PostJournalLine> lines = new ArrayList<>();
        // Debit side: salaries expense (gross) + employer statutory expense.
        lines.add(PostJournalLine.debit(GlAccountKeys.SALARIES_EXPENSE, gross, "Gross salaries and wages"));
        if (employerContrib.signum() > 0) {
            lines.add(PostJournalLine.debit(GlAccountKeys.EMPLOYER_STATUTORY_EXPENSE, employerContrib,
                    "Employer statutory contributions (NSSF + AHL + NITA)"));
        }
        // Credit side: net pay + statutory liabilities + other deductions.
        addIfPositive(lines, PostJournalLine.credit(GlAccountKeys.NET_PAY_PAYABLE, net, "Net pay payable"));
        addIfPositive(lines, PostJournalLine.credit(GlAccountKeys.PAYE_PAYABLE, paye, "PAYE payable"));
        addIfPositive(lines, PostJournalLine.credit(GlAccountKeys.NSSF_PAYABLE, nssf, "NSSF payable (employee + employer)"));
        addIfPositive(lines, PostJournalLine.credit(GlAccountKeys.SHIF_PAYABLE, shif, "SHIF payable"));
        addIfPositive(lines, PostJournalLine.credit(GlAccountKeys.AHL_PAYABLE, ahl, "Housing levy payable (employee + employer)"));
        addIfPositive(lines, PostJournalLine.credit(GlAccountKeys.NITA_PAYABLE, nita, "NITA payable"));
        addIfPositive(lines, PostJournalLine.credit(GlAccountKeys.OTHER_DEDUCTIONS_PAYABLE, other, "Other employee deductions payable"));

        // Reconciliation guard: debit total must equal credit total exactly. This
        // mirrors the payroll engine identity (gross + employer contributions =
        // net + paye + nssf + shif + ahl + nita + other). Any mismatch is rejected
        // and surfaced, never plugged with a rounding entry.
        BigDecimal debitTotal = gross.add(employerContrib);
        BigDecimal creditTotal = net.add(paye).add(nssf).add(shif).add(ahl).add(nita).add(other);
        if (debitTotal.compareTo(creditTotal) != 0) {
            throw new GlException("Payroll run " + id + " does not reconcile: debit " + debitTotal
                    + " (gross + employer contributions) != credit " + creditTotal
                    + " (net + statutory liabilities + other deductions). Investigate before posting.");
        }
        return cmd(GlSourceType.PAYROLL_RUN, id, p, lines, actor, label);
    }

    // ----------------------------------------------------------------- helpers

    private PostJournalCommand cmd(GlSourceType type, Long id, GlPostingPayload p,
                                   List<PostJournalLine> lines, User actor, String label) {
        return new PostJournalCommand(
                type, id, p.accountingDate(), p.referenceNumber(), p.description(),
                lines, actor, label);
    }

    private static void addIfPositive(List<PostJournalLine> lines, PostJournalLine line) {
        BigDecimal v = line.debit().signum() > 0 ? line.debit() : line.credit();
        if (v.signum() > 0) {
            lines.add(line);
        }
    }

    private static BigDecimal positive(BigDecimal v, String what) {
        if (v == null || v.signum() <= 0) {
            throw new GlException("A positive amount is required to post a " + what);
        }
        return v;
    }
}
