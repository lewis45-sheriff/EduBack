package com.EduePoa.EP.HR.compensation.dto.request;

import com.EduePoa.EP.HR.compensation.enums.AmountBasis;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AllowanceRequest {

    @NotBlank(message = "Allowance name is required")
    private String name;

    @NotNull(message = "Allowance basis is required")
    private AmountBasis basis;

    @NotNull(message = "Allowance amount is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Allowance amount cannot be negative")
    private BigDecimal amount;

    private boolean taxable = true;
    private boolean pensionable = false;
    private boolean fixedMonthly = false;
}
