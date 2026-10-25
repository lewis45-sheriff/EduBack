package com.EduePoa.EP.Budget;

import com.EduePoa.EP.Budget.DTO.Response.BudgetDashboardResponse;
import com.EduePoa.EP.Budget.DTO.Response.BudgetLineVarianceResponse;
import com.EduePoa.EP.Budget.DTO.Response.BudgetVarianceResponse;
import com.EduePoa.EP.Budget.Entity.Budget;
import com.EduePoa.EP.Budget.Entity.BudgetCategory;
import com.EduePoa.EP.Budget.Entity.BudgetLine;
import com.EduePoa.EP.Budget.Enum.CategoryStatus;
import com.EduePoa.EP.Budget.Enum.CategoryType;
import com.EduePoa.EP.Budget.Enum.PerformanceStatus;
import com.EduePoa.EP.Budget.Repository.BudgetLineRepository;
import com.EduePoa.EP.Budget.Repository.BudgetRepository;
import com.EduePoa.EP.Budget.Variance.BudgetActualsService;
import com.EduePoa.EP.Budget.Variance.BudgetDashboardService;
import com.EduePoa.EP.Budget.Variance.BudgetThresholdConfig;
import com.EduePoa.EP.Budget.Variance.BudgetVarianceCalculator;
import com.EduePoa.EP.Budget.Variance.BudgetVarianceService;
import com.EduePoa.EP.Budget.Variance.MappingStatus;
import com.EduePoa.EP.Budget.Variance.PerformanceClassifier;
import com.EduePoa.EP.GeneralLedger.entity.GlAccount;
import com.EduePoa.EP.GeneralLedger.enums.GlAccountType;
import com.EduePoa.EP.GeneralLedger.enums.GlNormalBalance;
import com.EduePoa.EP.GeneralLedger.repository.GlAccountRepository;
import com.EduePoa.EP.GeneralLedger.repository.JournalLineRepository;
import com.EduePoa.EP.GeneralLedger.repository.projection.AccountBalanceRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration-style unit tests for the actuals/variance assembly and the dashboard/report
 * derivation. Repositories are mocked so we assert the mapping resolution, GL sign handling,
 * roll-ups, data-quality counts, and the guarantee that dashboard/report figures mirror the
 * canonical variance exactly (design §4, §6, §7, §10).
 */
class BudgetVarianceServiceTest {

    private BudgetRepository budgetRepository;
    private BudgetLineRepository budgetLineRepository;
    private GlAccountRepository glAccountRepository;
    private JournalLineRepository journalLineRepository;

    private BudgetVarianceService varianceService;
    private BudgetDashboardService dashboardService;

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 4, 30);

    @BeforeEach
    void setUp() {
        budgetRepository = mock(BudgetRepository.class);
        budgetLineRepository = mock(BudgetLineRepository.class);
        glAccountRepository = mock(GlAccountRepository.class);
        journalLineRepository = mock(JournalLineRepository.class);

        BudgetActualsService actualsService = new BudgetActualsService(glAccountRepository, journalLineRepository);
        BudgetThresholdConfig cfg = new BudgetThresholdConfig();
        PerformanceClassifier classifier = new PerformanceClassifier(cfg);
        BudgetVarianceCalculator calculator = new BudgetVarianceCalculator(classifier);
        varianceService = new BudgetVarianceService(budgetRepository, budgetLineRepository,
                actualsService, calculator, classifier);
        dashboardService = new BudgetDashboardService(budgetRepository, varianceService);
    }

    private Budget budget() {
        Budget b = new Budget();
        b.setId(1L);
        b.setName("2026 T1");
        b.setStartDate(FROM);
        b.setEndDate(TO);
        return b;
    }

    private BudgetCategory category(Long id, String name, CategoryType type, String mappingKey) {
        return BudgetCategory.builder()
                .id(id).name(name).type(type).status(CategoryStatus.ACTIVE).mappingKey(mappingKey)
                .build();
    }

    private BudgetLine line(Long id, BudgetCategory cat, String allocated) {
        return BudgetLine.builder().id(id).budget(budget()).category(cat)
                .allocatedAmount(new BigDecimal(allocated)).build();
    }

    private GlAccount account(Long id, String key, GlAccountType type) {
        return GlAccount.builder().id(id).code(key).name(key).systemKey(key)
                .accountType(type)
                .normalBalance(type == GlAccountType.INCOME ? GlNormalBalance.CREDIT : GlNormalBalance.DEBIT)
                .active(true).build();
    }

    private AccountBalanceRow row(Long accountId, String debit, String credit) {
        return new AccountBalanceRow() {
            public Long getAccountId() { return accountId; }
            public BigDecimal getTotalDebit() { return new BigDecimal(debit); }
            public BigDecimal getTotalCredit() { return new BigDecimal(credit); }
        };
    }

    @Test
    @DisplayName("Mapped expense + income lines get correct signed actuals and separated totals")
    void mappedActuals() {
        Budget b = budget();
        BudgetCategory exp = category(10L, "Salaries", CategoryType.EXPENSE, "SALARIES_EXPENSE");
        BudgetCategory inc = category(20L, "Fees", CategoryType.INCOME, "FEES_INCOME");
        GlAccount expAcc = account(100L, "SALARIES_EXPENSE", GlAccountType.EXPENSE);
        GlAccount incAcc = account(200L, "FEES_INCOME", GlAccountType.INCOME);

        when(budgetRepository.findById(1L)).thenReturn(Optional.of(b));
        when(budgetLineRepository.findByBudgetId(1L)).thenReturn(List.of(
                line(1L, exp, "1000.00"), line(2L, inc, "5000.00")));
        when(glAccountRepository.findBySystemKey("SALARIES_EXPENSE")).thenReturn(Optional.of(expAcc));
        when(glAccountRepository.findBySystemKey("FEES_INCOME")).thenReturn(Optional.of(incAcc));
        // Expense account: debit-heavy net +800. Income account: credit-heavy net -4000.
        when(journalLineRepository.movementsBetween(FROM, TO)).thenReturn(List.of(
                row(100L, "800.00", "0.00"), row(200L, "0.00", "4000.00")));

        BudgetVarianceResponse v = varianceService.computeForBudget(b);

        BudgetLineVarianceResponse expLine = v.getLines().stream()
                .filter(l -> l.getCategoryId().equals(10L)).findFirst().orElseThrow();
        BudgetLineVarianceResponse incLine = v.getLines().stream()
                .filter(l -> l.getCategoryId().equals(20L)).findFirst().orElseThrow();

        assertThat(expLine.getActualAmount()).isEqualByComparingTo("800.00");
        assertThat(expLine.getMappingStatus()).isEqualTo(MappingStatus.MAPPED);
        assertThat(incLine.getActualAmount()).isEqualByComparingTo("4000.00"); // -(-4000)
        assertThat(v.getTotalExpenseApproved()).isEqualByComparingTo("1000.00");
        assertThat(v.getTotalExpenseActual()).isEqualByComparingTo("800.00");
        assertThat(v.getTotalIncomeApproved()).isEqualByComparingTo("5000.00");
        assertThat(v.getTotalIncomeActual()).isEqualByComparingTo("4000.00");
        assertThat(v.getExpenseUtilizationPercent()).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("Unmapped and invalid-type mapping yield zero actuals and are flagged + counted")
    void unmappedAndInvalid() {
        Budget b = budget();
        BudgetCategory unmapped = category(10L, "Misc", CategoryType.EXPENSE, null);
        BudgetCategory invalid = category(20L, "Wrong", CategoryType.EXPENSE, "FEES_INCOME");
        // FEES_INCOME is an INCOME account; mapping it from an EXPENSE category is INVALID.
        GlAccount incAcc = account(200L, "FEES_INCOME", GlAccountType.INCOME);

        when(budgetRepository.findById(1L)).thenReturn(Optional.of(b));
        when(budgetLineRepository.findByBudgetId(1L)).thenReturn(List.of(
                line(1L, unmapped, "300.00"), line(2L, invalid, "400.00")));
        when(glAccountRepository.findBySystemKey("FEES_INCOME")).thenReturn(Optional.of(incAcc));
        when(journalLineRepository.movementsBetween(FROM, TO)).thenReturn(List.of());

        BudgetVarianceResponse v = varianceService.computeForBudget(b);

        assertThat(v.getUnmappedCount()).isEqualTo(1);
        assertThat(v.getInvalidMappingCount()).isEqualTo(1);
        assertThat(v.getLines()).allSatisfy(l -> assertThat(l.getActualAmount()).isEqualByComparingTo("0.00"));
    }

    @Test
    @DisplayName("Empty GL aggregate: mapped line reports zero actual, not missing")
    void emptyGlAggregate() {
        Budget b = budget();
        BudgetCategory exp = category(10L, "Salaries", CategoryType.EXPENSE, "SALARIES_EXPENSE");
        GlAccount expAcc = account(100L, "SALARIES_EXPENSE", GlAccountType.EXPENSE);

        when(budgetRepository.findById(1L)).thenReturn(Optional.of(b));
        when(budgetLineRepository.findByBudgetId(1L)).thenReturn(List.of(line(1L, exp, "1000.00")));
        when(glAccountRepository.findBySystemKey("SALARIES_EXPENSE")).thenReturn(Optional.of(expAcc));
        when(journalLineRepository.movementsBetween(FROM, TO)).thenReturn(List.of());

        BudgetVarianceResponse v = varianceService.computeForBudget(b);
        BudgetLineVarianceResponse l = v.getLines().get(0);
        assertThat(l.getActualAmount()).isEqualByComparingTo("0.00");
        assertThat(l.getMappingStatus()).isEqualTo(MappingStatus.MAPPED);
        assertThat(l.getPerformanceStatus()).isEqualTo(PerformanceStatus.UNDER_BUDGET);
    }

    @Test
    @DisplayName("Dashboard figures mirror the canonical variance exactly")
    void dashboardMirrorsVariance() {
        Budget b = budget();
        BudgetCategory exp = category(10L, "Salaries", CategoryType.EXPENSE, "SALARIES_EXPENSE");
        GlAccount expAcc = account(100L, "SALARIES_EXPENSE", GlAccountType.EXPENSE);

        when(budgetRepository.findById(1L)).thenReturn(Optional.of(b));
        when(budgetLineRepository.findByBudgetId(1L)).thenReturn(List.of(line(1L, exp, "1000.00")));
        when(glAccountRepository.findBySystemKey("SALARIES_EXPENSE")).thenReturn(Optional.of(expAcc));
        when(journalLineRepository.movementsBetween(FROM, TO)).thenReturn(List.of(row(100L, "1200.00", "0.00")));

        BudgetVarianceResponse v = varianceService.computeForBudget(b);
        var dashResp = dashboardService.dashboard(1L);
        BudgetDashboardResponse d = (BudgetDashboardResponse) dashResp.getEntity();

        assertThat(d.getTotalApprovedExpenditure()).isEqualByComparingTo(v.getTotalExpenseApproved());
        assertThat(d.getActualExpenditure()).isEqualByComparingTo(v.getTotalExpenseActual());
        assertThat(d.getExpenditureUtilizationPercent()).isEqualByComparingTo(v.getExpenseUtilizationPercent());
        assertThat(d.getOverBudgetCount()).isEqualTo(v.getOverBudgetCount());
        assertThat(d.getOverBudgetCount()).isEqualTo(1); // 120% > 100%
        assertThat(d.isDepartmentalAttributionUnavailable()).isTrue();
    }

    @Test
    @DisplayName("Report OVER_BUDGET slice returns only over-budget lines")
    void reportOverBudgetFilter() {
        Budget b = budget();
        BudgetCategory over = category(10L, "Salaries", CategoryType.EXPENSE, "SALARIES_EXPENSE");
        BudgetCategory under = category(20L, "Stationery", CategoryType.EXPENSE, "EXPENSE_GENERAL");
        GlAccount a1 = account(100L, "SALARIES_EXPENSE", GlAccountType.EXPENSE);
        GlAccount a2 = account(101L, "EXPENSE_GENERAL", GlAccountType.EXPENSE);

        when(budgetRepository.findById(1L)).thenReturn(Optional.of(b));
        when(budgetLineRepository.findByBudgetId(1L)).thenReturn(List.of(
                line(1L, over, "1000.00"), line(2L, under, "1000.00")));
        when(glAccountRepository.findBySystemKey("SALARIES_EXPENSE")).thenReturn(Optional.of(a1));
        when(glAccountRepository.findBySystemKey("EXPENSE_GENERAL")).thenReturn(Optional.of(a2));
        when(journalLineRepository.movementsBetween(FROM, TO)).thenReturn(List.of(
                row(100L, "1500.00", "0.00"), row(101L, "100.00", "0.00")));

        var resp = dashboardService.report(1L, null, BudgetDashboardService.ReportType.OVER_BUDGET);
        @SuppressWarnings("unchecked")
        List<BudgetLineVarianceResponse> lines = (List<BudgetLineVarianceResponse>) resp.getEntity();
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).getCategoryId()).isEqualTo(10L);
    }
}
