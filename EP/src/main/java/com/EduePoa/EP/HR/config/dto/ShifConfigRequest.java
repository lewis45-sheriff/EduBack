package com.EduePoa.EP.HR.config.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ShifConfigRequest {
    @NotNull
    private LocalDate effectiveFrom;
    @NotNull
    private BigDecimal rate;
    @NotNull
    private BigDecimal minimumContribution;
}
