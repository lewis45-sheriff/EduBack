package com.EduePoa.EP.HR.payroll.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PayslipResponse {
    private Long id;
    private Long payrollRunId;
    private Integer periodMonth;
    private Integer periodYear;
    private Long staffId;
    private String staffName;
    private String employeeNumber;

    private BigDecimal basicPay;
    private BigDecimal grossPay;

    private BigDecimal paye;
    private BigDecimal nssfEmployee;
    private BigDecimal shif;
    private BigDecimal ahlEmployee;
    private BigDecimal otherDeductions;

    private BigDecimal taxableIncome;
    private BigDecimal personalRelief;
    private BigDecimal insuranceRelief;
    private BigDecimal totalEmployeeDeductions;
    private BigDecimal netPay;

    private BigDecimal nssfEmployer;
    private BigDecimal ahlEmployer;
    private BigDecimal nita;
    private BigDecimal totalEmployerContributions;

    private boolean prorated;
    private Integer eligibleDays;
    private Integer totalDays;

    private List<PayslipLineResponse> lines;
}
