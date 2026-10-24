package com.EduePoa.EP.HR.compensation.dto.response;

import com.EduePoa.EP.HR.compensation.enums.AmountBasis;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AllowanceResponse {
    private Long id;
    private String name;
    private AmountBasis basis;
    private BigDecimal amount;
    private boolean taxable;
    private boolean pensionable;
    private boolean fixedMonthly;
}
