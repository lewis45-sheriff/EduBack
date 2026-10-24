package com.EduePoa.EP.HR.compensation.repository;

import com.EduePoa.EP.HR.compensation.entity.RecurringDeduction;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RecurringDeductionRepository extends TenantAwareRepository<RecurringDeduction, Long> {
    Optional<RecurringDeduction> findByIdAndCompensationId(Long id, Long compensationId);
}
