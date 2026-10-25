package com.EduePoa.EP.Budget.Variance;

import com.EduePoa.EP.Budget.DTO.Response.BudgetLineVarianceResponse;
import com.EduePoa.EP.Budget.Enum.CategoryType;
import com.EduePoa.EP.Budget.Enum.PerformanceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The single canonical calculation layer for budget-vs-actual figures (design §7). Pure and
 * side-effect free: given an approved baseline, original approved amount, actual GL movement,
 * committed amount, category type and mapping status, it produces a fully-populated
 * {@link BudgetLineVarianceResponse}. All monetary values are scale 2, HALF_UP; percentages are
 * scale 2, HALF_UP, and null when the denominator is zero.
 *
 * <p>This class is the ONLY place variance/utilization/available are computed, so budget detail,
 * dashboard, reports and exports can never disagree.
 */
@Component
@RequiredArgsConstructor
public class BudgetVarianceCalculator {

    public static final int MONEY_SCALE = 2;

    private final PerformanceClassifier classifier;

    /** Inputs for a single line calculation. Immutable value object. */
    public record LineInput(
            Long lineId,
            Long categoryId,
            String categoryName,
            CategoryType categoryType,
            Long orgUnitId,
            String orgUnitName,
            BigDecimal approvedAmount,
            BigDecimal originalApprovedAmount,
            BigDecimal actualAmount,
            BigDecimal committedAmount,
            boolean committedSupported,
            MappingStatus mappingStatus,
            String mappingKey,
            BigDecimal approachingThresholdPercent,
            BigDecimal overThresholdPercent
    ) {
    }

    public BudgetLineVarianceResponse calculate(LineInput in) {
        BigDecimal approved = money(in.approvedAmount());
        BigDecimal originalApproved = money(in.originalApprovedAmount() != null
                ? in.originalApprovedAmount() : in.approvedAmount());
        BigDecimal actual = money(in.actualAmount());
        BigDecimal committed = money(in.committedAmount());
        boolean income = in.categoryType() == CategoryType.INCOME;

        boolean zeroApproved = approved.signum() == 0;
        boolean unbudgeted = zeroApproved && actual.signum() != 0;

        BigDecimal utilization = zeroApproved ? null : percent(actual, approved);

        BigDecimal variance;
        BigDecimal available;
        BigDecimal incomeRemainingToTarget;
        if (income) {
            // Income: positive variance = exceeded target.
            variance = actual.subtract(approved);
            available = null;
            BigDecimal gap = approved.subtract(actual);
            incomeRemainingToTarget = gap.signum() > 0 ? gap : BigDecimal.ZERO.setScale(MONEY_SCALE);
        } else {
            // Expense: positive variance = under budget.
            variance = approved.subtract(actual);
            available = approved.subtract(actual).subtract(committed);
            incomeRemainingToTarget = null;
        }

        PerformanceStatus status = classifier.classify(in.categoryType(), approved, actual, utilization,
                in.approachingThresholdPercent(), in.overThresholdPercent());

        return BudgetLineVarianceResponse.builder()
                .lineId(in.lineId())
                .categoryId(in.categoryId())
                .categoryName(in.categoryName())
                .categoryType(in.categoryType())
                .orgUnitId(in.orgUnitId())
                .orgUnitName(in.orgUnitName())
                .approvedAmount(approved)
                .originalApprovedAmount(originalApproved)
                .actualAmount(actual)
                .committedAmount(committed)
                .committedSupported(in.committedSupported())
                .availableAmount(available)
                .varianceAmount(money(variance))
                .incomeRemainingToTarget(incomeRemainingToTarget)
                .utilizationPercent(utilization)
                .unbudgeted(unbudgeted)
                .mappingStatus(in.mappingStatus())
                .mappingKey(in.mappingKey())
                .performanceStatus(status)
                .build();
    }

    /** actual/approved*100 at scale 2 HALF_UP. Caller guarantees approved != 0. */
    public BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return null;
        }
        return money(numerator).multiply(new BigDecimal("100"))
                .divide(denominator, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal money(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
