package com.EduePoa.EP.Budget.Repository;

import com.EduePoa.EP.Budget.Entity.BudgetLine;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetLineRepository extends TenantAwareRepository<BudgetLine, Long> {

    List<BudgetLine> findByBudgetId(Long budgetId);

    boolean existsByBudgetIdAndCategoryId(Long budgetId, Long categoryId);

    /** Duplicate check for a specific (budget, category, orgUnit) pair where orgUnit is set. */
    boolean existsByBudgetIdAndCategoryIdAndOrgUnitId(Long budgetId, Long categoryId, Long orgUnitId);

    /** Duplicate check for the school-wide (null org unit) line of a category in a budget. */
    boolean existsByBudgetIdAndCategoryIdAndOrgUnitIsNull(Long budgetId, Long categoryId);

    long countByCategoryId(Long categoryId);

    long countByOrgUnitId(Long orgUnitId);

    void deleteByBudgetId(Long budgetId);
}
