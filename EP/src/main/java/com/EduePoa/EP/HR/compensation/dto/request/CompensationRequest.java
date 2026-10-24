package com.EduePoa.EP.HR.compensation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Set or replace an employee's compensation. Allowances and deductions provided here fully
 * replace the existing lines (a deliberate "replace" semantic so the admin sees exactly what
 * will be saved); the per-line endpoints support incremental edits.
 */
@Data
public class CompensationRequest {

    @NotNull(message = "staffId is required")
    private Long staffId;

    @NotNull(message = "basicSalary is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "basicSalary must be greater than zero")
    private BigDecimal basicSalary;

    private String currency = "KES";

    private BigDecimal nssfPensionablePayOverride;

    @DecimalMin(value = "0.0", inclusive = true, message = "insurancePremiumMonthly cannot be negative")
    private BigDecimal insurancePremiumMonthly;

    private Boolean active = true;

    private LocalDate effectiveFrom;

    @Valid
    private List<AllowanceRequest> allowances = new ArrayList<>();

    @Valid
    private List<RecurringDeductionRequest> recurringDeductions = new ArrayList<>();
}
