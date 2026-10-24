package com.EduePoa.EP.HR.payroll.repository;

import com.EduePoa.EP.HR.payroll.entity.PayslipLine;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayslipLineRepository extends TenantAwareRepository<PayslipLine, Long> {
    List<PayslipLine> findByPayslipIdOrderBySortOrderAsc(Long payslipId);
}
