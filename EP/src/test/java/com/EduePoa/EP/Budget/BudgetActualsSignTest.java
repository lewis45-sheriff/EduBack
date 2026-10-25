package com.EduePoa.EP.Budget;

import com.EduePoa.EP.Budget.Enum.CategoryType;
import com.EduePoa.EP.Budget.Variance.BudgetActualsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the GL signed-movement sign convention (design §6.2): for an EXPENSE account the
 * actual is the net movement (debit - credit); for an INCOME account it is the negated net
 * movement, so a normal credit balance presents as positive income.
 */
class BudgetActualsSignTest {

    private final BudgetActualsService service = new BudgetActualsService(null, null);

    @Test
    @DisplayName("Expense account: expenseActual = net movement (debit-heavy -> positive)")
    void expenseSign() {
        assertThat(service.signedActual(CategoryType.EXPENSE, new BigDecimal("750.00")))
                .isEqualByComparingTo("750.00");
    }

    @Test
    @DisplayName("Income account: incomeActual = -net movement (credit-heavy net is negative -> positive income)")
    void incomeSign() {
        // A fee-income account posted Dr Cash / Cr Fees Income accumulates credits, so its own
        // net movement (debit-credit) is negative; negating yields positive income.
        assertThat(service.signedActual(CategoryType.INCOME, new BigDecimal("-900.00")))
                .isEqualByComparingTo("900.00");
    }

    @Test
    @DisplayName("Null net movement coalesces to zero")
    void nullIsZero() {
        assertThat(service.signedActual(CategoryType.EXPENSE, null)).isEqualByComparingTo("0.00");
        assertThat(service.signedActual(CategoryType.INCOME, null)).isEqualByComparingTo("0.00");
    }
}
