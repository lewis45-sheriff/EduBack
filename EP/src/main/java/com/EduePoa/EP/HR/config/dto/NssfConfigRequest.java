package com.EduePoa.EP.HR.config.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class NssfConfigRequest {
    @NotNull
    private LocalDate effectiveFrom;
    @NotNull
    private BigDecimal lowerEarningsLimit;
    @NotNull
    private BigDecimal upperEarningsLimit;
    @NotNull
    private BigDecimal employeeRate;
    @NotNull
    private BigDecimal employerRate;
}
