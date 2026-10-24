package com.EduePoa.EP.HR.payroll.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Statutory remittance summary for a run. Employee deductions and employer contributions are kept
 * as separate figures so employer cost is never conflated with what is withheld from staff. These
 * totals are reconciled against the sum of the run's payslips.
 */
@Data
@Builder
public class StatutorySummaryResponse {
    private Long payrollRunId;
    private Integer periodMonth;
    private Integer periodYear;
    private Integer employeeCount;

    // Employee side (withheld from pay)
    private BigDecimal paye;
    private BigDecimal nssfEmployee;
    private BigDecimal shif;
    private BigDecimal ahlEmployee;
    private BigDecimal otherDeductions;
    private BigDecimal totalEmployeeDeductions;

    // Employer side (employer cost / liabilities)
    private BigDecimal nssfEmployer;
    private BigDecimal ahlEmployer;
    private BigDecimal nita;
    private BigDecimal totalEmployerContributions;

    // Remittance per statutory body (employee + employer where both apply)
    private BigDecimal nssfTotalRemittance;
    private BigDecimal ahlTotalRemittance;

    private BigDecimal totalGross;
    private BigDecimal totalNet;
    private BigDecimal totalEmployerCost;

    /** True when the payslip sums reconcile to the stored run totals. */
    private boolean reconciled;
    private String reconciliationNote;
}
