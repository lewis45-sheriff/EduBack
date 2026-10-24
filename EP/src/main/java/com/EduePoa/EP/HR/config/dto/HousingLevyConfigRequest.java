package com.EduePoa.EP.HR.config.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class HousingLevyConfigRequest {
    @NotNull
    private LocalDate effectiveFrom;
    @NotNull
    private BigDecimal employeeRate;
    @NotNull
    private BigDecimal employerRate;
    private boolean preTaxDeductible = true;
    private BigDecimal reliefRate = BigDecimal.ZERO;
}
