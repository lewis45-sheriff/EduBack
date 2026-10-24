package com.EduePoa.EP.HR.compensation.repository;

import com.EduePoa.EP.HR.compensation.entity.Allowance;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AllowanceRepository extends TenantAwareRepository<Allowance, Long> {
    Optional<Allowance> findByIdAndCompensationId(Long id, Long compensationId);
}
