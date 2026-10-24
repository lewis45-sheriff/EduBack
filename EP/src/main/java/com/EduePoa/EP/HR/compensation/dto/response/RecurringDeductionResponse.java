package com.EduePoa.EP.HR.compensation.dto.response;

import com.EduePoa.EP.HR.compensation.enums.AmountBasis;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class RecurringDeductionResponse {
    private Long id;
    private String name;
    private AmountBasis basis;
    private BigDecimal amount;
    private boolean preTax;
    private LocalDate startDate;
    private LocalDate endDate;
}
