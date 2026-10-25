package com.EduePoa.EP.GeneralLedger.service;

import com.EduePoa.EP.GeneralLedger.dto.AccountStatementDTO;
import com.EduePoa.EP.GeneralLedger.dto.GlAccountDTO;
import com.EduePoa.EP.GeneralLedger.dto.JournalEntryDTO;
import com.EduePoa.EP.GeneralLedger.dto.TrialBalanceDTO;
import com.EduePoa.EP.GeneralLedger.entity.GlAccount;
import com.EduePoa.EP.GeneralLedger.entity.JournalEntry;
import com.EduePoa.EP.GeneralLedger.enums.GlNormalBalance;
import com.EduePoa.EP.GeneralLedger.enums.GlSourceType;
import com.EduePoa.EP.GeneralLedger.repository.GlAccountRepository;
import com.EduePoa.EP.GeneralLedger.repository.JournalEntryRepository;
import com.EduePoa.EP.GeneralLedger.repository.JournalLineRepository;
import com.EduePoa.EP.GeneralLedger.repository.projection.AccountBalanceRow;
import com.EduePoa.EP.GeneralLedger.repository.projection.LedgerLineRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only GL reporting: chart of accounts with derived balances, trial balance,
 * account statements, and journal search/detail.
 * <p>
 * Balances are always derived from posted journal lines via SQL aggregation — the
 * service never stores a running balance on the account and never loads the full
 * journal history into memory. The trial-balance {@code balanced} flag reflects
 * the actual posted data; a false value is a genuine integrity signal.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GlQueryService {

    private final GlAccountRepository accountRepository;
    private final JournalEntryRepository journalRepository;
    private final JournalLineRepository lineRepository;

    // ------------------------------------------------------- chart of accounts

    /** Chart of accounts with each account's balance as of {@code asOf} (default today). */
    public List<GlAccountDTO> accountsWithBalances(LocalDate asOf, boolean activeOnly) {
        LocalDate effectiveAsOf = asOf != null ? asOf : LocalDate.now();
        Map<Long, AccountBalanceRow> balances = new HashMap<>();
        for (AccountBalanceRow row : lineRepository.balancesAsOf(effectiveAsOf)) {
            balances.put(row.getAccountId(), row);
        }
        List<GlAccount> accounts = accountRepository.findForListing(activeOnly);
        List<GlAccountDTO> result = new ArrayList<>(accounts.size());
        for (GlAccount a : accounts) {
            AccountBalanceRow b = balances.get(a.getId());
            BigDecimal debit = b != null ? nz(b.getTotalDebit()) : BigDecimal.ZERO;
            BigDecimal credit = b != null ? nz(b.getTotalCredit()) : BigDecimal.ZERO;
            BigDecimal signed = debit.subtract(credit);
            result.add(toAccountDto(a, debit, credit, signed));
        }
        return result;
    }

    // --------------------------------------------------------- trial balance

    public TrialBalanceDTO trialBalance(LocalDate asOf) {
        LocalDate effectiveAsOf = asOf != null ? asOf : LocalDate.now();
        Map<Long, AccountBalanceRow> balances = new HashMap<>();
        for (AccountBalanceRow row : lineRepository.balancesAsOf(effectiveAsOf)) {
            balances.put(row.getAccountId(), row);
        }
        List<GlAccount> accounts = accountRepository.findAllByOrderByCodeAsc();

        List<TrialBalanceDTO.Row> rows = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (GlAccount a : accounts) {
            AccountBalanceRow b = balances.get(a.getId());
            if (b == null) {
                continue; // no activity -> omit from trial balance
            }
            BigDecimal signed = nz(b.getTotalDebit()).subtract(nz(b.getTotalCredit()));
            if (signed.signum() == 0) {
                continue;
            }
            BigDecimal debitBal = signed.signum() > 0 ? signed : BigDecimal.ZERO;
            BigDecimal creditBal = signed.signum() < 0 ? signed.negate() : BigDecimal.ZERO;
            totalDebit = totalDebit.add(debitBal);
            totalCredit = totalCredit.add(creditBal);
            rows.add(TrialBalanceDTO.Row.builder()
                    .accountId(a.getId())
                    .code(a.getCode())
                    .name(a.getName())
                    .accountType(a.getAccountType().name())
                    .debitBalance(debitBal)
                    .creditBalance(creditBal)
                    .build());
        }
        boolean balanced = totalDebit.compareTo(totalCredit) == 0;
        return TrialBalanceDTO.builder()
                .asOf(effectiveAsOf)
                .rows(rows)
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .balanced(balanced)
                .build();
    }

    // -------------------------------------------------------- account statement

    public AccountStatementDTO accountStatement(Long accountId, LocalDate from, LocalDate to) {
        GlAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new GlException("Account not found: " + accountId));
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();

        BigDecimal opening = nz(lineRepository.signedOpeningBalance(accountId, effectiveFrom));
        List<LedgerLineRow> rows = lineRepository.statementRows(accountId, effectiveFrom, effectiveTo);

        List<AccountStatementDTO.Movement> movements = new ArrayList<>(rows.size());
        BigDecimal running = opening;
        for (LedgerLineRow r : rows) {
            running = running.add(nz(r.getDebit())).subtract(nz(r.getCredit()));
            movements.add(AccountStatementDTO.Movement.builder()
                    .journalId(r.getJournalId())
                    .entryNumber(r.getEntryNumber())
                    .accountingDate(r.getAccountingDate())
                    .sourceType(r.getSourceType())
                    .referenceNumber(r.getReferenceNumber())
                    .narration(r.getNarration())
                    .debit(nz(r.getDebit()))
                    .credit(nz(r.getCredit()))
                    .runningBalance(running)
                    .build());
        }
        return AccountStatementDTO.builder()
                .accountId(account.getId())
                .code(account.getCode())
                .name(account.getName())
                .from(effectiveFrom)
                .to(effectiveTo)
                .openingBalance(opening)
                .movements(movements)
                .closingBalance(running)
                .build();
    }

    // -------------------------------------------------------------- journals

    public Page<JournalEntryDTO> searchJournals(GlSourceType sourceType, LocalDate from,
                                                LocalDate to, Pageable pageable) {
        return journalRepository.search(sourceType, from, to, pageable).map(GlMapper::toJournalDto);
    }

    public JournalEntryDTO journalDetail(Long id) {
        JournalEntry j = journalRepository.findById(id)
                .orElseThrow(() -> new GlException("Journal not found: " + id));
        return GlMapper.toJournalDto(j);
    }

    // ----------------------------------------------------------------- helpers

    private GlAccountDTO toAccountDto(GlAccount a, BigDecimal debit, BigDecimal credit, BigDecimal signed) {
        BigDecimal normalAmount = a.getNormalBalance() == GlNormalBalance.DEBIT ? signed : signed.negate();
        return GlAccountDTO.builder()
                .id(a.getId())
                .code(a.getCode())
                .name(a.getName())
                .accountType(a.getAccountType())
                .normalBalance(a.getNormalBalance())
                .systemKey(a.getSystemKey())
                .active(a.isActive())
                .systemAccount(a.isSystemAccount())
                .description(a.getDescription())
                .totalDebit(debit)
                .totalCredit(credit)
                .signedBalance(signed)
                .normalBalanceAmount(normalAmount)
                .build();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
