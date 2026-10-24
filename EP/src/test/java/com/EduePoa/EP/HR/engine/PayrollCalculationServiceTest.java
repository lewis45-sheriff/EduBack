package com.EduePoa.EP.HR.engine;

import com.EduePoa.EP.HR.config.service.StatutoryConfigSnapshot;
import com.EduePoa.EP.HR.proration.service.ProrationPolicySnapshot;
import com.EduePoa.EP.HR.proration.service.ProrationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the deterministic statutory engine. These are pure (no Spring, no DB) and assert
 * figures hand-derived from the national-default schedule in {@link PayrollTestFixtures}, exercising
 * each statutory item, the order dependency (NSSF/SHIF/AHL reduce PAYE), NSSF tier boundaries,
 * the SHIF minimum, reliefs, rounding, and the negative-net blocking guard.
 */
class PayrollCalculationServiceTest {

    private final PayrollCalculationService engine = new PayrollCalculationService();
    private final StatutoryConfigSnapshot config = PayrollTestFixtures.defaultStatutory();
    private final ProrationPolicySnapshot policy = PayrollTestFixtures.calendarPolicy();

    private PayrollInput fullMonth(BigDecimal basic) {
        return PayrollInput.builder()
                .basicSalary(basic)
                .proration(fullPeriod())
                .build();
    }

    private static ProrationResult fullPeriod() {
        return ProrationResult.builder()
                .fullPeriod(true).eligibleDays(30).totalDays(30).fraction(BigDecimal.ONE).build();
    }

    @Test
    @DisplayName("Worked example: gross 100,000 produces the hand-derived statutory breakdown")
    void workedExample_100k() {
        PayslipBreakdown b = engine.compute(fullMonth(new BigDecimal("100000")), config, policy);

        assertThat(b.getGrossPay()).isEqualByComparingTo("100000.00");
        // NSSF: tier1 8000*6%=480, tier2 (72000-8000)*6%=3840 => 4320
        assertThat(b.getNssfTier1Employee()).isEqualByComparingTo("480.00");
        assertThat(b.getNssfTier2Employee()).isEqualByComparingTo("3840.00");
        assertThat(b.getNssfEmployee()).isEqualByComparingTo("4320.00");
        assertThat(b.getShif()).isEqualByComparingTo("2750.00");
        assertThat(b.getAhlEmployee()).isEqualByComparingTo("1500.00");
        // taxable = 100000 - (4320 + 2750 + 1500) = 91430 (AHL is pre-tax deductible)
        assertThat(b.getTaxableIncome()).isEqualByComparingTo("91430.00");
        // gross PAYE = 2400 + 2083.25 + 17729.10 = 22212.35
        assertThat(b.getGrossPaye()).isEqualByComparingTo("22212.35");
        assertThat(b.getPersonalRelief()).isEqualByComparingTo("2400.00");
        assertThat(b.getPayePayable()).isEqualByComparingTo("19812.35");
        // net = 100000 - (4320+2750+1500+19812.35)
        assertThat(b.getNetPay()).isEqualByComparingTo("71617.65");
        assertThat(b.isBlocking()).isFalse();
    }

    @Test
    @DisplayName("Employer contributions: NSSF employer mirrors employee, AHL 1.5%, NITA 50")
    void employerContributions() {
        PayslipBreakdown b = engine.compute(fullMonth(new BigDecimal("100000")), config, policy);
        assertThat(b.getNssfEmployer()).isEqualByComparingTo("4320.00");
        assertThat(b.getAhlEmployer()).isEqualByComparingTo("1500.00");
        assertThat(b.getNita()).isEqualByComparingTo("50.00");
        assertThat(b.getTotalEmployerContributions()).isEqualByComparingTo("5870.00");
        assertThat(b.getTotalEmployerCost()).isEqualByComparingTo("105870.00");
    }

    @Test
    @DisplayName("NSSF Tier II caps at the Upper Earnings Limit (pensionable above UEL adds nothing)")
    void nssfCapsAtUel() {
        PayslipBreakdown b = engine.compute(fullMonth(new BigDecimal("200000")), config, policy);
        // Capped: tier1 480 + tier2 3840 = 4320 regardless of how far above UEL.
        assertThat(b.getNssfEmployee()).isEqualByComparingTo("4320.00");
    }

    @Test
    @DisplayName("NSSF Tier I only when pensionable pay is at/below the Lower Earnings Limit")
    void nssfTier1Only() {
        PayslipBreakdown b = engine.compute(fullMonth(new BigDecimal("8000")), config, policy);
        assertThat(b.getNssfTier1Employee()).isEqualByComparingTo("480.00");
        assertThat(b.getNssfTier2Employee()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("SHIF falls back to the configured minimum for very low gross")
    void shifMinimumApplies() {
        // gross 5000 * 2.75% = 137.50 which is below the 300 minimum.
        PayslipBreakdown b = engine.compute(fullMonth(new BigDecimal("5000")), config, policy);
        assertThat(b.getShif()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("Low earner below the PAYE threshold pays zero PAYE after personal relief")
    void lowEarnerNoPaye() {
        PayslipBreakdown b = engine.compute(fullMonth(new BigDecimal("20000")), config, policy);
        // taxable well within the 10% band; gross PAYE < 2400 personal relief => payable floored at 0.
        assertThat(b.getPayePayable()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Insurance relief is 15% of premium, capped at the configured monthly cap")
    void insuranceReliefCapped() {
        PayrollInput input = PayrollInput.builder()
                .basicSalary(new BigDecimal("100000"))
                .insurancePremiumMonthly(new BigDecimal("60000")) // 15% = 9000, capped at 5000
                .proration(fullPeriod())
                .build();
        PayslipBreakdown b = engine.compute(input, config, policy);
        assertThat(b.getInsuranceRelief()).isEqualByComparingTo("5000.00");
        // payePayable = 22212.35 - 2400 - 5000 = 14812.35
        assertThat(b.getPayePayable()).isEqualByComparingTo("14812.35");
    }

    @Test
    @DisplayName("Taxable allowance increases gross and taxable income; non-taxable only increases gross")
    void allowanceTaxability() {
        PayrollInput input = PayrollInput.builder()
                .basicSalary(new BigDecimal("50000"))
                .allowances(List.of(
                        PayrollInput.EarningLine.builder().name("House").amount(new BigDecimal("10000"))
                                .taxable(true).pensionable(false).build(),
                        PayrollInput.EarningLine.builder().name("Reimbursement").amount(new BigDecimal("5000"))
                                .taxable(false).pensionable(false).build()))
                .proration(fullPeriod())
                .build();
        PayslipBreakdown b = engine.compute(input, config, policy);
        assertThat(b.getGrossPay()).isEqualByComparingTo("65000.00");
        assertThat(b.getProratedTaxableAllowances()).isEqualByComparingTo("10000.00");
        assertThat(b.getProratedNonTaxableAllowances()).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("A pre-tax other deduction reduces taxable income; a post-tax one only reduces net")
    void preVsPostTaxDeductions() {
        PayrollInput preTax = PayrollInput.builder()
                .basicSalary(new BigDecimal("100000"))
                .otherDeductions(List.of(PayrollInput.DeductionLine.builder()
                        .name("Pension top-up").amount(new BigDecimal("10000")).preTax(true).build()))
                .proration(fullPeriod())
                .build();
        PayrollInput postTax = PayrollInput.builder()
                .basicSalary(new BigDecimal("100000"))
                .otherDeductions(List.of(PayrollInput.DeductionLine.builder()
                        .name("SACCO").amount(new BigDecimal("10000")).preTax(false).build()))
                .proration(fullPeriod())
                .build();

        PayslipBreakdown pre = engine.compute(preTax, config, policy);
        PayslipBreakdown post = engine.compute(postTax, config, policy);

        // Pre-tax lowers taxable income (91430 - 10000 = 81430) so PAYE is lower than post-tax.
        assertThat(pre.getTaxableIncome()).isEqualByComparingTo("81430.00");
        assertThat(post.getTaxableIncome()).isEqualByComparingTo("91430.00");
        assertThat(pre.getPayePayable()).isLessThan(post.getPayePayable());
    }

    @Test
    @DisplayName("AHL as a relief (not pre-tax deductible) keeps taxable income higher and reduces PAYE payable")
    void ahlAsRelief() {
        StatutoryConfigSnapshot reliefConfig = PayrollTestFixtures.defaultStatutory();
        reliefConfig.setAhlPreTaxDeductible(false);
        reliefConfig.setAhlReliefRate(new BigDecimal("0.15"));

        PayslipBreakdown b = engine.compute(fullMonth(new BigDecimal("100000")), reliefConfig, policy);
        // AHL no longer reduces taxable income: taxable = 100000 - (4320 + 2750) = 92930
        assertThat(b.getTaxableIncome()).isEqualByComparingTo("92930.00");
        // AHL relief = 1500 * 15% = 225
        assertThat(b.getAhlRelief()).isEqualByComparingTo("225.00");
    }

    @Test
    @DisplayName("Deductions exceeding gross flag a blocking error rather than saving a negative net")
    void negativeNetIsBlocking() {
        PayrollInput input = PayrollInput.builder()
                .basicSalary(new BigDecimal("30000"))
                .otherDeductions(List.of(PayrollInput.DeductionLine.builder()
                        .name("Huge loan").amount(new BigDecimal("40000")).preTax(false).build()))
                .proration(fullPeriod())
                .build();
        PayslipBreakdown b = engine.compute(input, config, policy);
        assertThat(b.isBlocking()).isTrue();
        assertThat(b.getWarnings()).isNotEmpty();
    }

    @Test
    @DisplayName("Proration halves the eligible earnings and the gross before statutory calculation")
    void prorationAppliesToEarnings() {
        ProrationResult half = ProrationResult.builder()
                .fullPeriod(false).eligibleDays(15).totalDays(30)
                .fraction(new BigDecimal("0.5")).build();
        PayrollInput input = PayrollInput.builder()
                .basicSalary(new BigDecimal("100000"))
                .proration(half)
                .build();
        PayslipBreakdown b = engine.compute(input, config, policy);
        assertThat(b.getProratedBasic()).isEqualByComparingTo("50000.00");
        assertThat(b.getGrossPay()).isEqualByComparingTo("50000.00");
    }

    @Test
    @DisplayName("A fixed monthly allowance under PAY_IN_FULL is not prorated even in a partial month")
    void fixedAllowancePaidInFull() {
        ProrationPolicySnapshot payInFull = PayrollTestFixtures.calendarPolicy();
        payInFull.setFixedAllowanceRule(com.EduePoa.EP.HR.proration.enums.FixedAllowanceProrationRule.PAY_IN_FULL);

        ProrationResult half = ProrationResult.builder()
                .fullPeriod(false).eligibleDays(15).totalDays(30)
                .fraction(new BigDecimal("0.5")).build();
        PayrollInput input = PayrollInput.builder()
                .basicSalary(new BigDecimal("40000"))
                .allowances(List.of(PayrollInput.EarningLine.builder()
                        .name("Commuter").amount(new BigDecimal("6000"))
                        .taxable(true).fixedMonthly(true).build()))
                .proration(half)
                .build();
        PayslipBreakdown b = engine.compute(input, config, payInFull);
        // basic halved to 20000, but the fixed allowance paid in full at 6000 => gross 26000
        assertThat(b.getProratedBasic()).isEqualByComparingTo("20000.00");
        assertThat(b.getGrossPay()).isEqualByComparingTo("26000.00");
    }

    @Test
    @DisplayName("Zero gross produces zero statutory deductions, zero PAYE and zero relief")
    void zeroGross() {
        PayslipBreakdown b = engine.compute(fullMonth(BigDecimal.ZERO), config, policy);
        assertThat(b.getGrossPay()).isEqualByComparingTo("0.00");
        assertThat(b.getShif()).isEqualByComparingTo("0.00");
        assertThat(b.getPayePayable()).isEqualByComparingTo("0.00");
        assertThat(b.getPersonalRelief()).isEqualByComparingTo("0.00");
        assertThat(b.getNetPay()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Reprocessing the same inputs is deterministic (identical figures)")
    void deterministic() {
        PayslipBreakdown a = engine.compute(fullMonth(new BigDecimal("123456.78")), config, policy);
        PayslipBreakdown b = engine.compute(fullMonth(new BigDecimal("123456.78")), config, policy);
        assertThat(a.getNetPay()).isEqualByComparingTo(b.getNetPay());
        assertThat(a.getPayePayable()).isEqualByComparingTo(b.getPayePayable());
        assertThat(a.getGrossPay()).isEqualByComparingTo(b.getGrossPay());
    }
}
