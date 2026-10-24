package com.EduePoa.EP.HR.config.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PayeBandRequest {
    @NotNull
    private Integer sequence;
    @NotNull
    private BigDecimal lowerBound;
    /** Null = open-ended top band. */
    private BigDecimal upperBound;
    @NotNull
    private BigDecimal rate;
}
