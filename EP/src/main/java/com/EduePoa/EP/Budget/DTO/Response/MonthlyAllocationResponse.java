package com.EduePoa.EP.Budget.DTO.Response;

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
public class MonthlyAllocationResponse {
    private Long id;
    private int month;
    private Year year;
    private BigDecimal allocatedAmount;
}
