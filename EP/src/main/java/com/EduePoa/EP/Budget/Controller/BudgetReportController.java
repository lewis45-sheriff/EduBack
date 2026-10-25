package com.EduePoa.EP.Budget.Controller;

import com.EduePoa.EP.Budget.Alert.BudgetAlertService;
import com.EduePoa.EP.Budget.Enum.CategoryType;
import com.EduePoa.EP.Budget.Variance.BudgetDashboardService;
import com.EduePoa.EP.Budget.Variance.BudgetVarianceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only budget financial-performance reporting. All endpoints require
 * {@code budget_report:read}. Figures come from the canonical calculation layer
 * ({@code BudgetVarianceService}) so they match the dashboard and reports exactly.
 */
@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
public class BudgetReportController {

    private final BudgetVarianceService budgetVarianceService;
    private final BudgetDashboardService budgetDashboardService;
    private final BudgetAlertService budgetAlertService;

    /** Budget detail with line-level budget-vs-actual, separated income/expense totals. */
    @GetMapping("/{id}/variance")
    @PreAuthorize("hasPermission(null, 'budget_report:read')")
    public ResponseEntity<?> variance(@PathVariable Long id) {
        var response = budgetVarianceService.variance(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    /** School-wide dashboard summary for the budget's authoritative window. */
    @GetMapping("/{id}/dashboard")
    @PreAuthorize("hasPermission(null, 'budget_report:read')")
    public ResponseEntity<?> dashboard(@PathVariable Long id) {
        var response = budgetDashboardService.dashboard(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    /**
     * Filtered line-level report. Optional {@code type} (INCOME/EXPENSE) and {@code reportType}
     * (BUDGET_VS_ACTUAL default, EXPENSE_VARIANCE, INCOME_PERFORMANCE, OVER_BUDGET,
     * APPROACHING_LIMIT, UNMAPPED_OR_INVALID). All filtering is server-side.
     */
    @GetMapping("/{id}/report")
    @PreAuthorize("hasPermission(null, 'budget_report:read')")
    public ResponseEntity<?> report(@PathVariable Long id,
                                    @RequestParam(required = false) CategoryType type,
                                    @RequestParam(required = false) BudgetDashboardService.ReportType reportType) {
        var response = budgetDashboardService.report(id, type, reportType);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    /** Current active performance alerts (approaching/over) for the budget. Read-only. */
    @GetMapping("/{id}/alerts")
    @PreAuthorize("hasPermission(null, 'budget_report:read')")
    public ResponseEntity<?> alerts(@PathVariable Long id) {
        var response = budgetAlertService.activeAlerts(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    /**
     * Recompute (deduplicated) performance alerts for the budget and return the active set.
     * Idempotent — repeated calls never create duplicate alerts.
     */
    @PostMapping("/{id}/alerts/evaluate")
    @PreAuthorize("hasPermission(null, 'budget_report:read')")
    public ResponseEntity<?> evaluateAlerts(@PathVariable Long id) {
        var response = budgetAlertService.evaluate(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
