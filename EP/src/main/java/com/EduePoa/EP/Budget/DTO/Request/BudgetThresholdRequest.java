package com.EduePoa.EP.Budget.DTO.Request;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Per-budget override of the performance thresholds. Either field may be null to fall back to the
 * tenant/system default. {@code approachingPercent} must be &le; {@code overPercent}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetThresholdRequest {

    @DecimalMin(value = "0.00", message = "approachingPercent must be zero or positive")
    private BigDecimal approachingPercent;

    @DecimalMin(value = "0.00", message = "overPercent must be zero or positive")
    private BigDecimal overPercent;
}
