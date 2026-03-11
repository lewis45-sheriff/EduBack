package com.EduePoa.EP.FinanceTransaction.Response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class StatisticsDTO {
    private List<MonthlyFeeDTO> monthlyFees;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netBalance;
}
