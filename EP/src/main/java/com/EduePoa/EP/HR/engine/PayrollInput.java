package com.EduePoa.EP.HR.engine;

import com.EduePoa.EP.HR.proration.service.ProrationResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollInput {

    private BigDecimal basicSalary;

    @Builder.Default
    private List<EarningLine> allowances = new ArrayList<>();

    @Builder.Default
    private List<DeductionLine> otherDeductions = new ArrayList<>();

    @Builder.Default
    private BigDecimal insurancePremiumMonthly = BigDecimal.ZERO;

    private BigDecimal nssfPensionablePayOverride;

    private ProrationResult proration;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EarningLine {
        private String name;
        private BigDecimal amount;
        private boolean taxable;
        private boolean pensionable;
        private boolean fixedMonthly;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DeductionLine {
        private String name;
        private BigDecimal amount;
        private boolean preTax;
    }
}
