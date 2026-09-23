package com.EduePoa.EP.Budget.Repository;

import com.EduePoa.EP.Budget.Entity.BudgetLine;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetLineRepository extends TenantAwareRepository<BudgetLine, Long> {

    List<BudgetLine> findByBudgetId(Long budgetId);

    boolean existsByBudgetIdAndCategoryId(Long budgetId, Long categoryId);

    long countByCategoryId(Long categoryId);

    void deleteByBudgetId(Long budgetId);
}
