package com.EduePoa.EP.HR.payroll.repository;

import com.EduePoa.EP.HR.payroll.entity.PayrollRun;
import com.EduePoa.EP.HR.payroll.enums.PayrollStatus;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayrollRunRepository extends TenantAwareRepository<PayrollRun, Long> {

    Optional<PayrollRun> findByPeriodYearAndPeriodMonth(Integer periodYear, Integer periodMonth);

    boolean existsByPeriodYearAndPeriodMonth(Integer periodYear, Integer periodMonth);

    Page<PayrollRun> findAllByOrderByPeriodYearDescPeriodMonthDesc(Pageable pageable);

    Page<PayrollRun> findByStatusOrderByPeriodYearDescPeriodMonthDesc(PayrollStatus status, Pageable pageable);
}
