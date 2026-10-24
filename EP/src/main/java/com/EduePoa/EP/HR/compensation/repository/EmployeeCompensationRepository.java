package com.EduePoa.EP.HR.compensation.repository;

import com.EduePoa.EP.HR.compensation.entity.EmployeeCompensation;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeCompensationRepository extends TenantAwareRepository<EmployeeCompensation, Long> {

    Optional<EmployeeCompensation> findByStaffId(Long staffId);

    boolean existsByStaffId(Long staffId);

    Page<EmployeeCompensation> findAll(Pageable pageable);

    /** All active compensation records — the candidate population for a payroll run. */
    List<EmployeeCompensation> findByActiveTrue();
}
