package com.EduePoa.EP.GeneralLedger.repository;

import com.EduePoa.EP.GeneralLedger.entity.JournalLine;
import com.EduePoa.EP.GeneralLedger.repository.projection.AccountBalanceRow;
import com.EduePoa.EP.GeneralLedger.repository.projection.LedgerLineRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Aggregate and statement queries over posted journal lines. Balances are always
 * derived here from the posted lines (the authoritative source), never from a
 * stored running balance. Only {@code POSTED} and {@code REVERSED} journals are
 * considered "posted" for accounting purposes — both are real, immutable history;
 * a reversal does not erase the original, it adds a counter-journal, and the
 * counter-journal has its own lines counted here too.
 */
public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    /**
     * Debit/credit totals per account, as of (inclusive) the given accounting
     * date, across all posted journals. One row per account that has any activity.
     */
    @Query("""
            select l.account.id as accountId,
                   coalesce(sum(l.debit), 0) as totalDebit,
                   coalesce(sum(l.credit), 0) as totalCredit
            from JournalLine l
            where l.journal.accountingDate <= :asOf
            group by l.account.id
            """)
    List<AccountBalanceRow> balancesAsOf(@Param("asOf") LocalDate asOf);

    /** Debit/credit totals for a single account as of (inclusive) a date. */
    @Query("""
            select coalesce(sum(l.debit), 0)
            from JournalLine l
            where l.account.id = :accountId and l.journal.accountingDate <= :asOf
            """)
    BigDecimal sumDebitAsOf(@Param("accountId") Long accountId, @Param("asOf") LocalDate asOf);

    @Query("""
            select coalesce(sum(l.credit), 0)
            from JournalLine l
            where l.account.id = :accountId and l.journal.accountingDate <= :asOf
            """)
    BigDecimal sumCreditAsOf(@Param("accountId") Long accountId, @Param("asOf") LocalDate asOf);

    /**
     * Opening balance (signed: debit minus credit) for an account strictly before
     * {@code from}. Used so a statement's running balance accounts for all prior
     * activity, not just the displayed page.
     */
    @Query("""
            select coalesce(sum(l.debit), 0) - coalesce(sum(l.credit), 0)
            from JournalLine l
            where l.account.id = :accountId and l.journal.accountingDate < :from
            """)
    BigDecimal signedOpeningBalance(@Param("accountId") Long accountId, @Param("from") LocalDate from);

    /**
     * Ordered movement rows for an account within an inclusive date range, oldest
     * first, for statement rendering. The running balance is computed in the
     * service on top of {@link #signedOpeningBalance}.
     */
    @Query("""
            select j.id as journalId,
                   j.entryNumber as entryNumber,
                   j.accountingDate as accountingDate,
                   j.sourceType as sourceType,
                   j.referenceNumber as referenceNumber,
                   l.narration as narration,
                   l.debit as debit,
                   l.credit as credit
            from JournalLine l join l.journal j
            where l.account.id = :accountId
              and j.accountingDate >= :from
              and j.accountingDate <= :to
            order by j.accountingDate asc, j.id asc, l.lineOrder asc
            """)
    List<LedgerLineRow> statementRows(@Param("accountId") Long accountId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    /**
     * Signed net movement ({@code sum(debit) - sum(credit)}) for a single account over an
     * inclusive accounting-date window {@code [from, to]}, across all posted journals
     * (both POSTED and REVERSED, plus reversal counter-journals, exactly as the rest of
     * this repository treats "posted"). Aggregated in the database and coalesced to zero;
     * never loads journal lines into memory. Used by the budgeting actuals service to
     * attribute GL activity to a budget category's mapped account for the budget window.
     */
    @Query("""
            select coalesce(sum(l.debit), 0) - coalesce(sum(l.credit), 0)
            from JournalLine l
            where l.account.id = :accountId
              and l.journal.accountingDate >= :from
              and l.journal.accountingDate <= :to
            """)
    BigDecimal signedMovementBetween(@Param("accountId") Long accountId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /**
     * Signed net movement per account over an inclusive window, for every account that has
     * any activity in the window. One row per account; used to assemble a whole budget's
     * actuals in a single query rather than one query per line. Coalesced and DB-aggregated.
     */
    @Query("""
            select l.account.id as accountId,
                   coalesce(sum(l.debit), 0) as totalDebit,
                   coalesce(sum(l.credit), 0) as totalCredit
            from JournalLine l
            where l.journal.accountingDate >= :from
              and l.journal.accountingDate <= :to
            group by l.account.id
            """)
    List<AccountBalanceRow> movementsBetween(@Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /** Grand totals of all posted debits/credits as of a date — for trial-balance integrity. */
    @Query("""
            select coalesce(sum(l.debit), 0)
            from JournalLine l
            where l.journal.accountingDate <= :asOf
            """)
    BigDecimal grandTotalDebit(@Param("asOf") LocalDate asOf);

    @Query("""
            select coalesce(sum(l.credit), 0)
            from JournalLine l
            where l.journal.accountingDate <= :asOf
            """)
    BigDecimal grandTotalCredit(@Param("asOf") LocalDate asOf);
}
