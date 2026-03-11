package com.EduePoa.EP.FinanceTransaction.Response;

import lombok.*;

import java.math.BigDecimal;

@Setter
@Getter
@AllArgsConstructor
public class MonthlyFeeDTO {
    private String month;
    private BigDecimal amount;


}
