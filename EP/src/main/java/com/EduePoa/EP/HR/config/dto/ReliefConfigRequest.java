package com.EduePoa.EP.HR.config.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ReliefConfigRequest {
    @NotNull
    private LocalDate effectiveFrom;
    @NotNull
    private BigDecimal personalReliefMonthly;
    @NotNull
    private BigDecimal insuranceReliefRate;
    @NotNull
    private BigDecimal insuranceReliefCap;
}
