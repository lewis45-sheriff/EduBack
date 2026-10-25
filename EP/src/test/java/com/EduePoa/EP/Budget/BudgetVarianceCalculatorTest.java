package com.EduePoa.EP.Budget;

import com.EduePoa.EP.Budget.DTO.Response.BudgetLineVarianceResponse;
import com.EduePoa.EP.Budget.Enum.CategoryType;
import com.EduePoa.EP.Budget.Enum.PerformanceStatus;
import com.EduePoa.EP.Budget.Variance.BudgetThresholdConfig;
import com.EduePoa.EP.Budget.Variance.BudgetVarianceCalculator;
import com.EduePoa.EP.Budget.Variance.MappingStatus;
import com.EduePoa.EP.Budget.Variance.PerformanceClassifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the canonical variance calculation layer and performance classifier.
 * Covers the sign conventions, zero-budget edge cases, utilization rounding, and threshold
 * boundary behaviour required by design §7 and §13.
 */
class BudgetVarianceCalculatorTest {

    private final BudgetVarianceCalculator calculator = newCalculator(new BigDecimal("80"), new BigDecimal("100"));

    private static BudgetVarianceCalculator newCalculator(BigDecimal approaching, BigDecimal over) {
        BudgetThresholdConfig cfg = new BudgetThresholdConfig();
        cfg.setApproachingPercent(approaching);
        cfg.setOverPercent(over);
        return new BudgetVarianceCalculator(new PerformanceClassifier(cfg));
    }

    private BudgetVarianceCalculator.LineInput input(CategoryType type, String approved, String actual) {
        return new BudgetVarianceCalculator.LineInput(
                1L, 10L, "Cat", type, null, null,
                new BigDecimal(approved), new BigDecimal(approved), new BigDecimal(actual),
                BigDecimal.ZERO, false, MappingStatus.MAPPED, "KEY", null, null);
    }

    @Test
    @DisplayName("Expense under budget: positive variance, available = approved - actual, UNDER_BUDGET")
    void expenseUnderBudget() {
        BudgetLineVarianceResponse r = calculator.calculate(input(CategoryType.EXPENSE, "1000.00", "600.00"));
        assertThat(r.getVarianceAmount()).isEqualByComparingTo("400.00");
        assertThat(r.getAvailableAmount()).isEqualByComparingTo("400.00");
        assertThat(r.getUtilizationPercent()).isEqualByComparingTo("60.00");
        assertThat(r.getPerformanceStatus()).isEqualTo(PerformanceStatus.UNDER_BUDGET);
        assertThat(r.isUnbudgeted()).isFalse();
    }

    @Test
    @DisplayName("Expense over budget: negative variance and OVER_BUDGET above 100%")
    void expenseOverBudget() {
        BudgetLineVarianceResponse r = calculator.calculate(input(CategoryType.EXPENSE, "1000.00", "1200.00"));
        assertThat(r.getVarianceAmount()).isEqualByComparingTo("-200.00");
        assertThat(r.getAvailableAmount()).isEqualByComparingTo("-200.00");
        assertThat(r.getUtilizationPercent()).isEqualByComparingTo("120.00");
        assertThat(r.getPerformanceStatus()).isEqualTo(PerformanceStatus.OVER_BUDGET);
    }

    @Test
    @DisplayName("Expense exactly at 100%: APPROACHING (AT_BUDGET), not OVER")
    void expenseAtFullUtilization() {
        BudgetLineVarianceResponse r = calculator.calculate(input(CategoryType.EXPENSE, "1000.00", "1000.00"));
        assertThat(r.getUtilizationPercent()).isEqualByComparingTo("100.00");
        assertThat(r.getPerformanceStatus()).isEqualTo(PerformanceStatus.AT_BUDGET);
    }

    @Test
    @DisplayName("Expense at 80% boundary: APPROACHING (AT_BUDGET)")
    void expenseAtApproachingBoundary() {
        BudgetLineVarianceResponse r = calculator.calculate(input(CategoryType.EXPENSE, "1000.00", "800.00"));
        assertThat(r.getUtilizationPercent()).isEqualByComparingTo("80.00");
        assertThat(r.getPerformanceStatus()).isEqualTo(PerformanceStatus.AT_BUDGET);
    }

    @Test
    @DisplayName("Zero approved with zero actual: utilization null, UNDER_BUDGET, not unbudgeted")
    void zeroBudgetZeroActual() {
        BudgetLineVarianceResponse r = calculator.calculate(input(CategoryType.EXPENSE, "0.00", "0.00"));
        assertThat(r.getUtilizationPercent()).isNull();
        assertThat(r.isUnbudgeted()).isFalse();
        assertThat(r.getPerformanceStatus()).isEqualTo(PerformanceStatus.UNDER_BUDGET);
    }

    @Test
    @DisplayName("Zero approved with non-zero actual: unbudgeted, OVER_BUDGET, null utilization")
    void zeroBudgetNonZeroActual() {
        BudgetLineVarianceResponse r = calculator.calculate(input(CategoryType.EXPENSE, "0.00", "500.00"));
        assertThat(r.getUtilizationPercent()).isNull();
        assertThat(r.isUnbudgeted()).isTrue();
        assertThat(r.getVarianceAmount()).isEqualByComparingTo("-500.00");
        assertThat(r.getPerformanceStatus()).isEqualTo(PerformanceStatus.OVER_BUDGET);
    }

    @Test
    @DisplayName("Income above target: positive variance, ABOVE_TARGET, no availableAmount")
    void incomeAboveTarget() {
        BudgetLineVarianceResponse r = calculator.calculate(input(CategoryType.INCOME, "1000.00", "1200.00"));
        assertThat(r.getVarianceAmount()).isEqualByComparingTo("200.00");
        assertThat(r.getAvailableAmount()).isNull();
        assertThat(r.getIncomeRemainingToTarget()).isEqualByComparingTo("0.00");
        assertThat(r.getPerformanceStatus()).isEqualTo(PerformanceStatus.ABOVE_TARGET);
    }

    @Test
    @DisplayName("Income below target: negative variance, BELOW_TARGET, remaining-to-target populated")
    void incomeBelowTarget() {
        BudgetLineVarianceResponse r = calculator.calculate(input(CategoryType.INCOME, "1000.00", "500.00"));
        assertThat(r.getVarianceAmount()).isEqualByComparingTo("-500.00");
        assertThat(r.getIncomeRemainingToTarget()).isEqualByComparingTo("500.00");
        assertThat(r.getUtilizationPercent()).isEqualByComparingTo("50.00");
        assertThat(r.getPerformanceStatus()).isEqualTo(PerformanceStatus.BELOW_TARGET);
    }

    @Test
    @DisplayName("Per-budget threshold override changes classification (approaching at 60%)")
    void perBudgetThresholdOverride() {
        BudgetVarianceCalculator.LineInput in = new BudgetVarianceCalculator.LineInput(
                1L, 10L, "Cat", CategoryType.EXPENSE, null, null,
                new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("650.00"),
                BigDecimal.ZERO, false, MappingStatus.MAPPED, "KEY",
                new BigDecimal("60"), new BigDecimal("90"));
        BudgetLineVarianceResponse r = calculator.calculate(in);
        // 65% utilization: with default 80/100 this would be UNDER, but override 60/90 makes it APPROACHING.
        assertThat(r.getUtilizationPercent()).isEqualByComparingTo("65.00");
        assertThat(r.getPerformanceStatus()).isEqualTo(PerformanceStatus.AT_BUDGET);
    }

    @Test
    @DisplayName("Utilization rounds HALF_UP to two decimals")
    void utilizationRounding() {
        // 1/3 * 100 = 33.333... -> 33.33
        BudgetLineVarianceResponse r = calculator.calculate(input(CategoryType.EXPENSE, "3.00", "1.00"));
        assertThat(r.getUtilizationPercent()).isEqualByComparingTo("33.33");
    }
}
