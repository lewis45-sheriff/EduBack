package com.EduePoa.EP.Budget.DTO.Request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetLineRequest {

    @NotNull(message = "categoryId is required")
    private Long categoryId;

    @NotNull(message = "allocatedAmount is required")
    @PositiveOrZero(message = "allocatedAmount must be zero or positive")
    private BigDecimal allocatedAmount;

    /** Optional monthly phasing; sum must not exceed allocatedAmount. */
    @Valid
    private List<MonthlyAllocationRequest> monthlyAllocations;
}
