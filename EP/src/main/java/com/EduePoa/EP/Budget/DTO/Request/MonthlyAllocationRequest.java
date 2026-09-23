package com.EduePoa.EP.Budget.DTO.Request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Year;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyAllocationRequest {

    @NotNull(message = "month is required")
    @Min(value = 1, message = "month must be between 1 and 12")
    @Max(value = 12, message = "month must be between 1 and 12")
    private Integer month;

    @NotNull(message = "year is required")
    private Year year;

    @NotNull(message = "allocatedAmount is required")
    @PositiveOrZero(message = "allocatedAmount must be zero or positive")
    private BigDecimal allocatedAmount;
}
