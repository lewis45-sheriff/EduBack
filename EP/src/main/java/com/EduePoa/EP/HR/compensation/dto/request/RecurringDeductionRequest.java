package com.EduePoa.EP.HR.compensation.dto.request;

import com.EduePoa.EP.HR.compensation.enums.AmountBasis;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RecurringDeductionRequest {

    @NotBlank(message = "Deduction name is required")
    private String name;

    @NotNull(message = "Deduction basis is required")
    private AmountBasis basis;

    @NotNull(message = "Deduction amount is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Deduction amount cannot be negative")
    private BigDecimal amount;

    private boolean preTax = false;
    private LocalDate startDate;
    private LocalDate endDate;
}
