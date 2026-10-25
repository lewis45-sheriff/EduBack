package com.EduePoa.EP.Budget.Variance;

import com.EduePoa.EP.Budget.Enum.CategoryType;
import com.EduePoa.EP.Budget.Enum.PerformanceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The single source of truth for turning an approved/actual pair into a
 * {@link PerformanceStatus}. Every dashboard, detail view and report classifies through
 * here so the same numbers always produce the same status (design §7.2, §13).
 *
 * <p>EXPENSE lines map to UNDER_BUDGET / AT_BUDGET / OVER_BUDGET; INCOME lines to
 * BELOW_TARGET / AT_TARGET / ABOVE_TARGET. Thresholds come from {@link BudgetThresholdConfig}.
 */
@Component
@RequiredArgsConstructor
public class PerformanceClassifier {

    private final BudgetThresholdConfig thresholds;

    /**
     * Classify a line. {@code utilizationPercent} may be null (zero approved amount); in that
     * case a non-zero actual is treated as the exceptional unbudgeted/over condition and a zero
     * actual as within/at.
     */
    public PerformanceStatus classify(CategoryType type, BigDecimal approved, BigDecimal actual,
                                      BigDecimal utilizationPercent) {
        return classify(type, approved, actual, utilizationPercent,
                thresholds.getApproachingPercent(), thresholds.getOverPercent());
    }

    /**
     * Classify with explicit effective thresholds (per-budget overrides resolved by the caller).
     * Null thresholds fall back to the system/tenant defaults.
     */
    public PerformanceStatus classify(CategoryType type, BigDecimal approved, BigDecimal actual,
                                      BigDecimal utilizationPercent,
                                      BigDecimal approachingPercent, BigDecimal overPercent) {
        boolean income = type == CategoryType.INCOME;
        BigDecimal approaching = approachingPercent != null ? approachingPercent : thresholds.getApproachingPercent();
        BigDecimal over = overPercent != null ? overPercent : thresholds.getOverPercent();

        // Zero approved amount: utilization is undefined. Distinguish unbudgeted activity.
        if (utilizationPercent == null) {
            boolean hasActual = actual != null && actual.signum() != 0;
            if (income) {
                // No target set. Any income is "above" an (implicit zero) target; none is "at".
                return hasActual ? PerformanceStatus.ABOVE_TARGET : PerformanceStatus.AT_TARGET;
            }
            // Expense: spending with no budget is the exceptional over-budget (unbudgeted) case.
            return hasActual ? PerformanceStatus.OVER_BUDGET : PerformanceStatus.UNDER_BUDGET;
        }

        int cmpApproaching = utilizationPercent.compareTo(approaching);
        int cmpOver = utilizationPercent.compareTo(over);

        if (income) {
            // Income: exceeding the target is good (ABOVE), below band is BELOW, within band is AT.
            if (cmpOver > 0) {
                return PerformanceStatus.ABOVE_TARGET;
            }
            if (cmpApproaching < 0) {
                return PerformanceStatus.BELOW_TARGET;
            }
            return PerformanceStatus.AT_TARGET;
        }

        // Expense.
        if (cmpOver > 0) {
            return PerformanceStatus.OVER_BUDGET;
        }
        if (cmpApproaching < 0) {
            return PerformanceStatus.UNDER_BUDGET;
        }
        return PerformanceStatus.AT_BUDGET;
    }

    /** True for the performance statuses that count as "approaching the limit" on a dashboard. */
    public boolean isApproaching(PerformanceStatus status) {
        return status == PerformanceStatus.AT_BUDGET;
    }

    /** True for the performance statuses that count as "over budget" on a dashboard (expense only). */
    public boolean isOver(PerformanceStatus status) {
        return status == PerformanceStatus.OVER_BUDGET;
    }
}
