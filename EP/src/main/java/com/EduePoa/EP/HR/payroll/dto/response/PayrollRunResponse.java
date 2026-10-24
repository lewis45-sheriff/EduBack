package com.EduePoa.EP.HR.payroll.dto.response;

import com.EduePoa.EP.HR.payroll.enums.PayrollStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PayrollRunResponse {
    private Long id;
    private Integer periodMonth;
    private Integer periodYear;
    private PayrollStatus status;

    private BigDecimal totalGross;
    private BigDecimal totalPaye;
    private BigDecimal totalNssfEmployee;
    private BigDecimal totalNssfEmployer;
    private BigDecimal totalShif;
    private BigDecimal totalAhlEmployee;
    private BigDecimal totalAhlEmployer;
    private BigDecimal totalNita;
    private BigDecimal totalOtherDeductions;
    private BigDecimal totalEmployeeDeductions;
    private BigDecimal totalEmployerContributions;
    private BigDecimal totalNet;
    private BigDecimal totalEmployerCost;
    private Integer employeeCount;

    private boolean hasBlockingErrors;
    private Integer prorationPolicyVersion;

    private String processedByName;
    private LocalDateTime processedAt;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private String approvalComment;
    private LocalDateTime paidAt;
    private String paymentReference;
    private LocalDateTime createdAt;

    /** Non-fatal validation messages aggregated across payslips from the last processing. */
    private List<String> warnings;
}
