package com.EduePoa.EP.Budget.Repository;

import com.EduePoa.EP.Budget.Entity.BudgetLineMonthlyAllocation;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetLineMonthlyAllocationRepository extends TenantAwareRepository<BudgetLineMonthlyAllocation, Long> {

    List<BudgetLineMonthlyAllocation> findByBudgetLineId(Long budgetLineId);

    void deleteByBudgetLineId(Long budgetLineId);
}
