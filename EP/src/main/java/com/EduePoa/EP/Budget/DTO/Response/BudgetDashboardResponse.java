package com.EduePoa.EP.Budget.DTO.Response;

import com.EduePoa.EP.Budget.Enum.BudgetStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * School-wide budget dashboard summary for a single budget's authoritative window (design §10.2).
 * Expense and income performance are reported separately — never merged into one utilization
 * figure. Data-quality counts (unmapped / invalid / unattributed) are surfaced so the UI never
 * presents an unreliable zero as real.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetDashboardResponse {

    private Long budgetId;
    private String budgetName;
    private BudgetStatus status;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    // ---- Expenditure ----
    private BigDecimal totalApprovedExpenditure;
    private BigDecimal actualExpenditure;
    private BigDecimal remainingExpenditure;
    /** actualExpenditure / totalApprovedExpenditure * 100 (scale 2); null if approved is zero. */
    private BigDecimal expenditureUtilizationPercent;

    // ---- Income ----
    private BigDecimal totalApprovedIncomeTarget;
    private BigDecimal actualIncome;
    private BigDecimal incomeTargetVariance;
    /** actualIncome / totalApprovedIncomeTarget * 100 (scale 2); null if target is zero. */
    private BigDecimal incomeAchievementPercent;

    // ---- Warning / data-quality counts ----
    private int overBudgetCount;
    private int approachingLimitCount;
    private int unmappedCategoryCount;
    private int invalidMappingCount;

    /**
     * Departmental actuals are not yet attributable from the GL (no org-unit dimension on journal
     * lines — design §6.4). Until that exists this is true and {@link #departmentBreakdown} is
     * empty, so the UI shows "departmental actuals unavailable" rather than a fabricated split.
     */
    private boolean departmentalAttributionUnavailable;

    /** Empty in Phase 2; populated in Phase 3 only where attribution is reliable. */
    private List<DepartmentSummary> departmentBreakdown;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DepartmentSummary {
        private Long orgUnitId;
        private String orgUnitName;
        private BigDecimal approvedExpenditure;
        private BigDecimal actualExpenditure;
        private BigDecimal remainingExpenditure;
        private BigDecimal utilizationPercent;
        /** True when some of this unit's actuals cannot be reliably attributed. */
        private boolean partiallyAttributed;
    }
}
