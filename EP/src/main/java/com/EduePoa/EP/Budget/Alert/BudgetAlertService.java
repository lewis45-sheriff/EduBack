package com.EduePoa.EP.Budget.Alert;

import com.EduePoa.EP.Budget.DTO.Response.BudgetLineVarianceResponse;
import com.EduePoa.EP.Budget.DTO.Response.BudgetVarianceResponse;
import com.EduePoa.EP.Budget.Entity.Budget;
import com.EduePoa.EP.Budget.Enum.PerformanceStatus;
import com.EduePoa.EP.Budget.Repository.BudgetRepository;
import com.EduePoa.EP.Budget.Variance.BudgetVarianceService;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Evaluates a budget's lines against the canonical variance and maintains a deduplicated set of
 * {@link BudgetAlert}s for APPROACHING / OVER conditions (design §14 Phase 4).
 *
 * <p>Evaluation is idempotent: an unchanged condition updates the existing alert's figures and
 * {@code lastEvaluatedAt} instead of inserting a duplicate; a cleared condition flips the alert to
 * inactive. This means the dashboard (or a scheduled job) can call {@link #evaluate(Long)} as often
 * as it likes without generating duplicate notifications.
 */
@Service
@RequiredArgsConstructor
public class BudgetAlertService {

    private final BudgetRepository budgetRepository;
    private final BudgetVarianceService varianceService;
    private final BudgetAlertRepository alertRepository;

    /**
     * Recompute alerts for a budget. Returns the current active alert set. Safe to call repeatedly;
     * deduplicates on (budget, line, status).
     */
    @Transactional
    public CustomResponse<?> evaluate(Long budgetId) {
        CustomResponse<List<BudgetAlert>> response = new CustomResponse<>();
        Budget budget = budgetRepository.findById(budgetId).orElse(null);
        if (budget == null) {
            response.setMessage("Budget not found with id: " + budgetId);
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            return response;
        }
        BudgetVarianceResponse v = varianceService.computeForBudget(budget);
        LocalDateTime now = LocalDateTime.now();

        // Track which (line,status) pairs are currently in an alert band so we can deactivate stale ones.
        Set<String> currentKeys = new HashSet<>();

        for (BudgetLineVarianceResponse line : v.getLines()) {
            PerformanceStatus status = line.getPerformanceStatus();
            boolean alertable = status == PerformanceStatus.AT_BUDGET || status == PerformanceStatus.OVER_BUDGET;
            if (!alertable) {
                continue;
            }
            currentKeys.add(line.getLineId() + ":" + status.name());
            upsert(budgetId, line, status, now);
        }

        // Deactivate alerts whose condition no longer holds (do not delete — keep history/audit trail).
        for (BudgetAlert existing : alertRepository.findByBudgetIdAndActive(budgetId, true)) {
            String key = existing.getBudgetLineId() + ":" + existing.getAlertStatus().name();
            if (!currentKeys.contains(key)) {
                existing.setActive(false);
                existing.setLastEvaluatedAt(now);
                alertRepository.save(existing);
            }
        }

        response.setMessage("Budget alerts evaluated successfully");
        response.setEntity(alertRepository.findByBudgetIdAndActive(budgetId, true));
        response.setStatusCode(HttpStatus.OK.value());
        return response;
    }

    @Transactional(readOnly = true)
    public CustomResponse<?> activeAlerts(Long budgetId) {
        CustomResponse<List<BudgetAlert>> response = new CustomResponse<>();
        response.setMessage("Active budget alerts fetched successfully");
        response.setEntity(alertRepository.findByBudgetIdAndActive(budgetId, true));
        response.setStatusCode(HttpStatus.OK.value());
        return response;
    }

    private void upsert(Long budgetId, BudgetLineVarianceResponse line, PerformanceStatus status,
                        LocalDateTime now) {
        BudgetAlert alert = alertRepository
                .findByBudgetIdAndBudgetLineIdAndAlertStatus(budgetId, line.getLineId(), status)
                .orElse(null);
        if (alert == null) {
            alert = BudgetAlert.builder()
                    .budgetId(budgetId)
                    .budgetLineId(line.getLineId())
                    .categoryName(line.getCategoryName())
                    .orgUnitName(line.getOrgUnitName())
                    .alertStatus(status)
                    .firstRaisedAt(now)
                    .active(true)
                    .build();
        }
        alert.setApprovedAmount(line.getApprovedAmount());
        alert.setActualAmount(line.getActualAmount());
        alert.setUtilizationPercent(line.getUtilizationPercent());
        alert.setCategoryName(line.getCategoryName());
        alert.setOrgUnitName(line.getOrgUnitName());
        alert.setActive(true);
        alert.setLastEvaluatedAt(now);
        alertRepository.save(alert);
    }
}
