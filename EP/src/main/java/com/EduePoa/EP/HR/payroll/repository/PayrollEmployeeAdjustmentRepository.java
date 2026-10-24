package com.EduePoa.EP.HR.payroll.repository;

import com.EduePoa.EP.HR.payroll.entity.PayrollEmployeeAdjustment;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollEmployeeAdjustmentRepository
        extends TenantAwareRepository<PayrollEmployeeAdjustment, Long> {

    List<PayrollEmployeeAdjustment> findByPayrollRunId(Long payrollRunId);

    Optional<PayrollEmployeeAdjustment> findByPayrollRunIdAndStaffId(Long payrollRunId, Long staffId);
}
