package com.EduePoa.EP.HR.compensation.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class CompensationResponse {
    private Long id;
    private Long staffId;
    private String staffName;
    private String employeeNumber;
    private BigDecimal basicSalary;
    private String currency;
    private BigDecimal nssfPensionablePayOverride;
    private BigDecimal insurancePremiumMonthly;
    private boolean active;
    private LocalDate effectiveFrom;
    private List<AllowanceResponse> allowances;
    private List<RecurringDeductionResponse> recurringDeductions;
}
