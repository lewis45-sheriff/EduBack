package com.EduePoa.EP.Budget.Variance;

import com.EduePoa.EP.Budget.DTO.Response.BudgetDashboardResponse;
import com.EduePoa.EP.Budget.DTO.Response.BudgetLineVarianceResponse;
import com.EduePoa.EP.Budget.DTO.Response.BudgetVarianceResponse;
import com.EduePoa.EP.Budget.Entity.Budget;
import com.EduePoa.EP.Budget.Enum.CategoryType;
import com.EduePoa.EP.Budget.Enum.PerformanceStatus;
import com.EduePoa.EP.Budget.Repository.BudgetRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the school-wide dashboard summary and filtered report views from the single canonical
 * variance computation ({@link BudgetVarianceService}). Nothing here re-derives financial figures;
 * it only summarizes and filters the already-computed line variances, so the dashboard, detail
 * view and reports can never disagree (design §10.2, §10.3).
 */
@Service
@RequiredArgsConstructor
public class BudgetDashboardService {

    private final BudgetRepository budgetRepository;
    private final BudgetVarianceService varianceService;

    @Transactional(readOnly = true)
    public CustomResponse<?> dashboard(Long budgetId) {
        CustomResponse<BudgetDashboardResponse> response = new CustomResponse<>();
        Budget budget = budgetRepository.findById(budgetId).orElse(null);
        if (budget == null) {
            response.setMessage("Budget not found with id: " + budgetId);
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            return response;
        }
        BudgetVarianceResponse v = varianceService.computeForBudget(budget);

        BudgetDashboardResponse dash = BudgetDashboardResponse.builder()
                .budgetId(v.getBudgetId())
                .budgetName(v.getBudgetName())
                .status(v.getStatus())
                .periodStart(v.getPeriodStart())
                .periodEnd(v.getPeriodEnd())
                .totalApprovedExpenditure(v.getTotalExpenseApproved())
                .actualExpenditure(v.getTotalExpenseActual())
                .remainingExpenditure(v.getTotalExpenseAvailable())
                .expenditureUtilizationPercent(v.getExpenseUtilizationPercent())
                .totalApprovedIncomeTarget(v.getTotalIncomeApproved())
                .actualIncome(v.getTotalIncomeActual())
                .incomeTargetVariance(v.getTotalIncomeVariance())
                .incomeAchievementPercent(v.getIncomeAchievementPercent())
                .overBudgetCount(v.getOverBudgetCount())
                .approachingLimitCount(v.getApproachingLimitCount())
                .unmappedCategoryCount(v.getUnmappedCount())
                .invalidMappingCount(v.getInvalidMappingCount())
                // No org-unit dimension on GL journal lines yet (design §6.4).
                .departmentalAttributionUnavailable(true)
                .departmentBreakdown(Collections.emptyList())
                .build();

        response.setMessage("Budget dashboard computed successfully");
        response.setEntity(dash);
        response.setStatusCode(HttpStatus.OK.value());
        return response;
    }

    /**
     * Filtered line-level report. {@code type} narrows to INCOME or EXPENSE lines (null = both);
     * {@code reportType} selects a performance slice. All filtering runs server-side over the
     * canonical variance lines.
     */
    @Transactional(readOnly = true)
    public CustomResponse<?> report(Long budgetId, CategoryType type, ReportType reportType) {
        CustomResponse<List<BudgetLineVarianceResponse>> response = new CustomResponse<>();
        Budget budget = budgetRepository.findById(budgetId).orElse(null);
        if (budget == null) {
            response.setMessage("Budget not found with id: " + budgetId);
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            return response;
        }
        BudgetVarianceResponse v = varianceService.computeForBudget(budget);
        ReportType effective = reportType != null ? reportType : ReportType.BUDGET_VS_ACTUAL;

        List<BudgetLineVarianceResponse> filtered = v.getLines().stream()
                .filter(l -> type == null || l.getCategoryType() == type)
                .filter(l -> matchesReport(l, effective))
                .collect(Collectors.toList());

        response.setMessage("Budget report computed successfully");
        response.setEntity(filtered);
        response.setStatusCode(HttpStatus.OK.value());
        return response;
    }

    private boolean matchesReport(BudgetLineVarianceResponse line, ReportType reportType) {
        return switch (reportType) {
            case BUDGET_VS_ACTUAL -> true;
            case OVER_BUDGET -> line.getPerformanceStatus() == PerformanceStatus.OVER_BUDGET;
            case APPROACHING_LIMIT -> line.getPerformanceStatus() == PerformanceStatus.AT_BUDGET;
            case EXPENSE_VARIANCE -> line.getCategoryType() == CategoryType.EXPENSE;
            case INCOME_PERFORMANCE -> line.getCategoryType() == CategoryType.INCOME;
            case UNMAPPED_OR_INVALID -> line.getMappingStatus() != MappingStatus.MAPPED;
        };
    }

    /** Report slices supported by the reporting endpoint. */
    public enum ReportType {
        BUDGET_VS_ACTUAL,
        EXPENSE_VARIANCE,
        INCOME_PERFORMANCE,
        OVER_BUDGET,
        APPROACHING_LIMIT,
        UNMAPPED_OR_INVALID
    }
}
