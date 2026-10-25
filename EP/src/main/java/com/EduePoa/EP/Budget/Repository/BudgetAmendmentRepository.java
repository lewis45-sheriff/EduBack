package com.EduePoa.EP.Budget.Repository;

import com.EduePoa.EP.Budget.Entity.BudgetAmendment;
import com.EduePoa.EP.Budget.Enum.AmendmentStatus;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetAmendmentRepository extends TenantAwareRepository<BudgetAmendment, Long> {

    Page<BudgetAmendment> findByBudgetId(Long budgetId, Pageable pageable);

    List<BudgetAmendment> findByBudgetIdAndStatus(Long budgetId, AmendmentStatus status);
}
