package com.EduePoa.EP.Budget.Alert;

import com.EduePoa.EP.Budget.Enum.PerformanceStatus;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetAlertRepository extends TenantAwareRepository<BudgetAlert, Long> {

    Optional<BudgetAlert> findByBudgetIdAndBudgetLineIdAndAlertStatus(
            Long budgetId, Long budgetLineId, PerformanceStatus alertStatus);

    List<BudgetAlert> findByBudgetId(Long budgetId);

    List<BudgetAlert> findByBudgetIdAndActive(Long budgetId, boolean active);
}
