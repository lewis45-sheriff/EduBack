package com.EduePoa.EP.HR.payroll.repository;

import com.EduePoa.EP.HR.payroll.entity.Payslip;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayslipRepository extends TenantAwareRepository<Payslip, Long> {

    List<Payslip> findByPayrollRunId(Long payrollRunId);

    void deleteByPayrollRunId(Long payrollRunId);

    List<Payslip> findByStaffIdOrderByIdDesc(Long staffId);

    /** Self-service guard: a payslip is only the caller's if it belongs to their staff id. */
    Optional<Payslip> findByIdAndStaffId(Long id, Long staffId);
}
