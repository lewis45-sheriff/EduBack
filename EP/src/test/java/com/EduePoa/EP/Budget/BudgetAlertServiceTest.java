package com.EduePoa.EP.Budget;

import com.EduePoa.EP.Budget.Alert.BudgetAlert;
import com.EduePoa.EP.Budget.Alert.BudgetAlertRepository;
import com.EduePoa.EP.Budget.Alert.BudgetAlertService;
import com.EduePoa.EP.Budget.DTO.Response.BudgetLineVarianceResponse;
import com.EduePoa.EP.Budget.DTO.Response.BudgetVarianceResponse;
import com.EduePoa.EP.Budget.Entity.Budget;
import com.EduePoa.EP.Budget.Enum.PerformanceStatus;
import com.EduePoa.EP.Budget.Repository.BudgetRepository;
import com.EduePoa.EP.Budget.Variance.BudgetVarianceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that budget alert evaluation is idempotent and deduplicated (design §14 Phase 4): repeated
 * evaluation of the same condition never creates a duplicate alert, and a cleared condition flips
 * the alert inactive instead of leaving a stale active alert.
 */
class BudgetAlertServiceTest {

    private BudgetRepository budgetRepository;
    private BudgetVarianceService varianceService;
    private BudgetAlertService alertService;

    private final List<BudgetAlert> store = new ArrayList<>();
    private long seq = 1;

    @BeforeEach
    void setUp() {
        budgetRepository = mock(BudgetRepository.class);
        varianceService = mock(BudgetVarianceService.class);
        BudgetAlertRepository alertRepository = mock(BudgetAlertRepository.class);

        Budget b = new Budget();
        b.setId(1L);
        when(budgetRepository.findById(1L)).thenReturn(Optional.of(b));

        // In-memory alert store backing the mocked repository.
        when(alertRepository.save(any(BudgetAlert.class))).thenAnswer(inv -> {
            BudgetAlert a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(seq++);
                store.add(a);
            }
            return a;
        });
        when(alertRepository.findByBudgetIdAndBudgetLineIdAndAlertStatus(any(), any(), any()))
                .thenAnswer(inv -> store.stream()
                        .filter(a -> a.getBudgetId().equals(inv.getArgument(0))
                                && a.getBudgetLineId().equals(inv.getArgument(1))
                                && a.getAlertStatus() == inv.getArgument(2))
                        .findFirst());
        when(alertRepository.findByBudgetIdAndActive(anyLong(), anyBoolean()))
                .thenAnswer(inv -> store.stream()
                        .filter(a -> a.getBudgetId().equals(inv.getArgument(0))
                                && a.isActive() == (boolean) inv.getArgument(1))
                        .toList());

        alertService = new BudgetAlertService(budgetRepository, varianceService, alertRepository);
    }

    private BudgetLineVarianceResponse line(Long id, PerformanceStatus status, String util) {
        return BudgetLineVarianceResponse.builder()
                .lineId(id).categoryName("Cat" + id)
                .performanceStatus(status)
                .approvedAmount(new BigDecimal("1000.00"))
                .actualAmount(new BigDecimal("1100.00"))
                .utilizationPercent(util == null ? null : new BigDecimal(util))
                .build();
    }

    private void stubVariance(BudgetLineVarianceResponse... lines) {
        BudgetVarianceResponse v = BudgetVarianceResponse.builder().budgetId(1L).lines(List.of(lines)).build();
        when(varianceService.computeForBudget(any())).thenReturn(v);
    }

    @Test
    @DisplayName("Re-evaluating an unchanged over-budget condition does not duplicate the alert")
    void idempotentNoDuplicate() {
        stubVariance(line(10L, PerformanceStatus.OVER_BUDGET, "110.00"));

        alertService.evaluate(1L);
        alertService.evaluate(1L);
        alertService.evaluate(1L);

        assertThat(store).hasSize(1);
        assertThat(store.get(0).isActive()).isTrue();
        assertThat(store.get(0).getAlertStatus()).isEqualTo(PerformanceStatus.OVER_BUDGET);
    }

    @Test
    @DisplayName("A cleared condition deactivates the alert instead of leaving it active")
    void clearedConditionDeactivates() {
        stubVariance(line(10L, PerformanceStatus.OVER_BUDGET, "110.00"));
        alertService.evaluate(1L);
        assertThat(store.get(0).isActive()).isTrue();

        // Next evaluation: line is now under budget -> alert should deactivate, not duplicate.
        stubVariance(line(10L, PerformanceStatus.UNDER_BUDGET, "40.00"));
        alertService.evaluate(1L);

        assertThat(store).hasSize(1);
        assertThat(store.get(0).isActive()).isFalse();
    }

    @Test
    @DisplayName("Approaching and over conditions on different lines each raise one alert")
    void separateAlertsPerLine() {
        stubVariance(
                line(10L, PerformanceStatus.OVER_BUDGET, "120.00"),
                line(20L, PerformanceStatus.AT_BUDGET, "85.00"),
                line(30L, PerformanceStatus.UNDER_BUDGET, "30.00"));

        alertService.evaluate(1L);

        assertThat(store).hasSize(2);
        assertThat(store.stream().filter(BudgetAlert::isActive).count()).isEqualTo(2);
    }
}
