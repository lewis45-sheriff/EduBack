package com.EduePoa.EP.Budget.Variance;

import com.EduePoa.EP.Budget.DTO.Response.BudgetLineVarianceResponse;
import com.EduePoa.EP.Budget.DTO.Response.BudgetVarianceResponse;
import com.EduePoa.EP.Budget.Entity.Budget;
import com.EduePoa.EP.Budget.Entity.BudgetCategory;
import com.EduePoa.EP.Budget.Entity.BudgetLine;
import com.EduePoa.EP.Budget.Enum.CategoryType;
import com.EduePoa.EP.Budget.Repository.BudgetLineRepository;
import com.EduePoa.EP.Budget.Repository.BudgetRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Assembles the canonical budget-vs-actual view for a budget. Loads the budget and its lines,
 * attributes GL actuals per line via {@link BudgetActualsService} (one aggregation query for the
 * whole window), runs each line through the single {@link BudgetVarianceCalculator}, and rolls up
 * separated INCOME / EXPENSE totals plus warning counts. Dashboard and reports reuse this so a
 * figure never changes between screens (design §4, §7, §10.1).
 */
@Service
@RequiredArgsConstructor
public class BudgetVarianceService {

    private final BudgetRepository budgetRepository;
    private final BudgetLineRepository budgetLineRepository;
    private final BudgetActualsService actualsService;
    private final BudgetVarianceCalculator calculator;
    private final PerformanceClassifier classifier;

    @Transactional(readOnly = true)
    public CustomResponse<?> variance(Long budgetId) {
        CustomResponse<BudgetVarianceResponse> response = new CustomResponse<>();
        Budget budget = budgetRepository.findById(budgetId).orElse(null);
        if (budget == null) {
            response.setMessage("Budget not found with id: " + budgetId);
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            return response;
        }
        BudgetVarianceResponse body = computeForBudget(budget);
        response.setMessage("Budget variance computed successfully");
        response.setEntity(body);
        response.setStatusCode(HttpStatus.OK.value());
        return response;
    }

    /**
     * Compute the full variance view for an already-loaded budget. Public so the dashboard and
     * report services can reuse the exact same calculation path.
     */
    @Transactional(readOnly = true)
    public BudgetVarianceResponse computeForBudget(Budget budget) {
        LocalDate from = budget.getStartDate();
        LocalDate to = budget.getEndDate();

        // Single GL aggregation for the window; each mapped line reads from this map.
        Map<Long, BigDecimal> netByAccount = actualsService.netMovementByAccount(from, to);

        List<BudgetLine> budgetLines = budgetLineRepository.findByBudgetId(budget.getId());
        List<BudgetLineVarianceResponse> lines = new ArrayList<>(budgetLines.size());

        BigDecimal totalExpApproved = BigDecimal.ZERO;
        BigDecimal totalExpActual = BigDecimal.ZERO;
        BigDecimal totalExpAvailable = BigDecimal.ZERO;
        BigDecimal totalIncApproved = BigDecimal.ZERO;
        BigDecimal totalIncActual = BigDecimal.ZERO;

        int overCount = 0;
        int approachingCount = 0;
        int unmappedCount = 0;
        int invalidCount = 0;

        for (BudgetLine line : budgetLines) {
            BudgetCategory category = line.getCategory();
            CategoryType type = category.getType();

            BudgetActualsService.ResolvedMapping resolved =
                    actualsService.resolveMapping(category.getMappingKey(), type);

            BigDecimal actual;
            if (resolved.status() == MappingStatus.MAPPED) {
                BigDecimal net = netByAccount.getOrDefault(resolved.account().getId(), BigDecimal.ZERO);
                actual = actualsService.signedActual(type, net);
            } else {
                actual = BigDecimal.ZERO;
            }

            BudgetLineVarianceResponse lineVariance = calculator.calculate(new BudgetVarianceCalculator.LineInput(
                    line.getId(),
                    category.getId(),
                    category.getName(),
                    type,
                    line.getOrgUnit() != null ? line.getOrgUnit().getId() : null,
                    line.getOrgUnit() != null ? line.getOrgUnit().getName() : null,
                    line.getAllocatedAmount(),
                    line.getOriginalAllocatedAmount() != null
                            ? line.getOriginalAllocatedAmount() : line.getAllocatedAmount(),
                    actual,
                    BigDecimal.ZERO,           // committed not supported in Phase 1
                    false,
                    resolved.status(),
                    category.getMappingKey(),
                    budget.getApproachingThresholdPercent(),
                    budget.getOverThresholdPercent()
            ));
            lines.add(lineVariance);

            if (type == CategoryType.INCOME) {
                totalIncApproved = totalIncApproved.add(lineVariance.getApprovedAmount());
                totalIncActual = totalIncActual.add(lineVariance.getActualAmount());
            } else {
                totalExpApproved = totalExpApproved.add(lineVariance.getApprovedAmount());
                totalExpActual = totalExpActual.add(lineVariance.getActualAmount());
                if (lineVariance.getAvailableAmount() != null) {
                    totalExpAvailable = totalExpAvailable.add(lineVariance.getAvailableAmount());
                }
            }

            switch (lineVariance.getMappingStatus()) {
                case UNMAPPED -> unmappedCount++;
                case INVALID_MAPPING -> invalidCount++;
                default -> {
                }
            }
            if (classifier.isOver(lineVariance.getPerformanceStatus())) {
                overCount++;
            } else if (classifier.isApproaching(lineVariance.getPerformanceStatus())) {
                approachingCount++;
            }
        }

        BigDecimal expUtil = pct(totalExpActual, totalExpApproved);
        BigDecimal incAchievement = pct(totalIncActual, totalIncApproved);

        return BudgetVarianceResponse.builder()
                .budgetId(budget.getId())
                .budgetName(budget.getName())
                .status(budget.getStatus())
                .academicTerm(budget.getAcademicTerm())
                .academicYear(budget.getAcademicYear())
                .periodStart(from)
                .periodEnd(to)
                .totalExpenseApproved(money(totalExpApproved))
                .totalExpenseActual(money(totalExpActual))
                .totalExpenseAvailable(money(totalExpAvailable))
                .totalExpenseVariance(money(totalExpApproved.subtract(totalExpActual)))
                .expenseUtilizationPercent(expUtil)
                .totalIncomeApproved(money(totalIncApproved))
                .totalIncomeActual(money(totalIncActual))
                .totalIncomeVariance(money(totalIncActual.subtract(totalIncApproved)))
                .incomeAchievementPercent(incAchievement)
                .overBudgetCount(overCount)
                .approachingLimitCount(approachingCount)
                .unmappedCount(unmappedCount)
                .invalidMappingCount(invalidCount)
                .lines(lines)
                .build();
    }

    private BigDecimal pct(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return null;
        }
        return money(numerator).multiply(new BigDecimal("100"))
                .divide(money(denominator), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
