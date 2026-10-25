package com.EduePoa.EP.Budget.Variance;

import com.EduePoa.EP.Budget.Enum.CategoryType;
import com.EduePoa.EP.GeneralLedger.entity.GlAccount;
import com.EduePoa.EP.GeneralLedger.enums.GlAccountType;
import com.EduePoa.EP.GeneralLedger.repository.GlAccountRepository;
import com.EduePoa.EP.GeneralLedger.repository.JournalLineRepository;
import com.EduePoa.EP.GeneralLedger.repository.projection.AccountBalanceRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves actual financial activity for budget categories from the General Ledger — the single
 * authoritative source of posted activity (design §6). Never scans source transaction tables.
 *
 * <p>A category's {@code mappingKey} is interpreted as a GL account {@code systemKey}. The mapped
 * account is validated to belong to the current tenant and to be type-compatible with the
 * category (EXPENSE category → EXPENSE account; INCOME category → INCOME account). Actuals are the
 * signed net movement {@code sum(debit) - sum(credit)} over the inclusive budget accounting-date
 * window:
 * <ul>
 *   <li>EXPENSE account: {@code expenseActual = netMovement}</li>
 *   <li>INCOME account:  {@code incomeActual  = -netMovement}</li>
 * </ul>
 * An unmapped or invalid-mapped category yields zero actuals and a non-MAPPED status, never a
 * silent zero. All queries run under the request's Hibernate tenant filter.
 */
@Service
@RequiredArgsConstructor
public class BudgetActualsService {

    private final GlAccountRepository accountRepository;
    private final JournalLineRepository journalLineRepository;

    /** Outcome of attributing GL actuals to one category over a window. */
    public record CategoryActual(BigDecimal actualAmount, MappingStatus mappingStatus,
                                 String mappingKey, Long accountId) {
    }

    /**
     * Resolve the mapped account for a category mappingKey + type, validating tenant ownership
     * (implicit via the tenant filter on the repository) and type compatibility. Returns null when
     * the mapping is absent or invalid; the status explains which.
     */
    public ResolvedMapping resolveMapping(String mappingKey, CategoryType categoryType) {
        if (!StringUtils.hasText(mappingKey)) {
            return new ResolvedMapping(null, MappingStatus.UNMAPPED);
        }
        GlAccount account = accountRepository.findBySystemKey(mappingKey.trim()).orElse(null);
        if (account == null || !account.isActive()) {
            return new ResolvedMapping(null, MappingStatus.INVALID_MAPPING);
        }
        if (!isTypeCompatible(categoryType, account.getAccountType())) {
            return new ResolvedMapping(null, MappingStatus.INVALID_MAPPING);
        }
        return new ResolvedMapping(account, MappingStatus.MAPPED);
    }

    public record ResolvedMapping(GlAccount account, MappingStatus status) {
    }

    /**
     * Signed actual for one category over {@code [from, to]} (inclusive accounting dates).
     * Zero + flagged when the category is unmapped or the mapping is invalid.
     */
    @Transactional(readOnly = true)
    public CategoryActual actualFor(String mappingKey, CategoryType categoryType,
                                    LocalDate from, LocalDate to) {
        ResolvedMapping resolved = resolveMapping(mappingKey, categoryType);
        if (resolved.status() != MappingStatus.MAPPED) {
            return new CategoryActual(zero(), resolved.status(), mappingKey, null);
        }
        GlAccount account = resolved.account();
        BigDecimal net = nz(journalLineRepository.signedMovementBetween(account.getId(), from, to));
        BigDecimal actual = signedActual(categoryType, net);
        return new CategoryActual(actual, MappingStatus.MAPPED, mappingKey, account.getId());
    }

    /**
     * Batch variant: signed net movement per account over the window, keyed by account id. Lets a
     * caller resolve many categories against a single GL aggregation query instead of one per line.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> netMovementByAccount(LocalDate from, LocalDate to) {
        Map<Long, BigDecimal> result = new HashMap<>();
        for (AccountBalanceRow row : journalLineRepository.movementsBetween(from, to)) {
            result.put(row.getAccountId(), nz(row.getTotalDebit()).subtract(nz(row.getTotalCredit())));
        }
        return result;
    }

    /** Apply the category-type sign convention to a raw signed net movement. */
    public BigDecimal signedActual(CategoryType categoryType, BigDecimal netMovement) {
        BigDecimal net = nz(netMovement);
        BigDecimal actual = categoryType == CategoryType.INCOME ? net.negate() : net;
        return actual.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isTypeCompatible(CategoryType categoryType, GlAccountType accountType) {
        return categoryType == CategoryType.INCOME
                ? accountType == GlAccountType.INCOME
                : accountType == GlAccountType.EXPENSE;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
