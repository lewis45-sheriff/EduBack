package com.EduePoa.EP.Budget.DTO.Response;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Budget.Enum.BudgetStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

/**
 * Canonical budget-level budget-vs-actual view: budget header, the reporting window used to
 * attribute actuals, separated INCOME and EXPENSE totals, and per-line performance. Income and
 * expense are never merged into a single utilization figure (see design §7.1).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetVarianceResponse {

    private Long budgetId;
    private String budgetName;
    private BudgetStatus status;
    private Term academicTerm;
    private Year academicYear;

    /** The authoritative accounting-date window actuals were aggregated over (inclusive). */
    private LocalDate periodStart;
    private LocalDate periodEnd;

    // ---- Expense totals (sum over EXPENSE lines) ----
    private BigDecimal totalExpenseApproved;
    private BigDecimal totalExpenseActual;
    private BigDecimal totalExpenseAvailable;
    private BigDecimal totalExpenseVariance;
    /** totalExpenseActual / totalExpenseApproved * 100 (scale 2); null if approved is zero. */
    private BigDecimal expenseUtilizationPercent;

    // ---- Income totals (sum over INCOME lines) ----
    private BigDecimal totalIncomeApproved;
    private BigDecimal totalIncomeActual;
    private BigDecimal totalIncomeVariance;
    /** totalIncomeActual / totalIncomeApproved * 100 (scale 2); null if target is zero. */
    private BigDecimal incomeAchievementPercent;

    // ---- Warning counts ----
    private int overBudgetCount;
    private int approachingLimitCount;
    private int unmappedCount;
    private int invalidMappingCount;

    private List<BudgetLineVarianceResponse> lines;
}
