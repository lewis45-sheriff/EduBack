package com.EduePoa.EP.Expenses.Responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseSummaryDTO {

    private BigDecimal totalAmount;
    private Long totalCount;
    private BigDecimal approvedAmount;
    private BigDecimal pendingAmount;
    private LocalDate startDate;
    private LocalDate endDate;
}
