package com.EduePoa.EP.HR.engine;

import com.EduePoa.EP.HR.config.service.StatutoryConfigSnapshot;
import com.EduePoa.EP.HR.proration.enums.FixedAllowanceProrationRule;
import com.EduePoa.EP.HR.proration.service.ProrationPolicySnapshot;
import com.EduePoa.EP.HR.proration.service.ProrationResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The deterministic Kenyan statutory payroll engine.
 * <p>
 * This is a <strong>pure</strong> calculator: it takes a {@link PayrollInput}, the frozen
 * {@link StatutoryConfigSnapshot} and the frozen {@link ProrationPolicySnapshot}, and returns a
 * {@link PayslipBreakdown}. It never touches the database, the tenant context, the clock, or any
 * mutable state, so it is fully unit-testable and a run is reproducible from its stored snapshots.
 * <p>
 * Computation order (order-dependent, because NSSF/SHIF/AHL reduce PAYE taxable income):
 * <ol>
 *   <li>Apply proration to eligible earnings (basic + allowances, honouring the fixed-allowance rule).</li>
 *   <li>Gross = prorated basic + prorated taxable + prorated non-taxable allowances.</li>
 *   <li>NSSF employee (Tier I + Tier II) on pensionable pay.</li>
 *   <li>SHIF = rate x gross, floored at the minimum.</li>
 *   <li>AHL employee = rate x gross; employer = rate x gross.</li>
 *   <li>Taxable income = gross - (NSSF + SHIF + pre-tax other deductions [+ AHL if pre-tax deductible]).</li>
 *   <li>Gross PAYE = graduated bands applied to taxable income.</li>
 *   <li>PAYE payable = gross PAYE - personal relief - insurance relief - AHL relief (floored at 0).</li>
 *   <li>Net = gross - (NSSF + SHIF + AHL + PAYE + other deductions), floored with a blocking warning
 *       if it would go negative.</li>
 *   <li>Employer contributions (NSSF employer, AHL employer, NITA) computed separately.</li>
 * </ol>
 * All money is {@link BigDecimal}; the rounding mode and scale come from the proration policy so
 * the statutory rounding is an explicit, auditable choice.
 */
@Service
public class PayrollCalculationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public PayslipBreakdown compute(PayrollInput input,
                                    StatutoryConfigSnapshot config,
                                    ProrationPolicySnapshot prorationPolicy) {
        if (input == null || config == null || prorationPolicy == null) {
            throw new IllegalArgumentException("input, config and proration policy are all required");
        }
        int scale = prorationPolicy.getRoundingScale();
        RoundingMode rm = prorationPolicy.getRounding().toRoundingMode();

        ProrationResult proration = input.getProration();
        BigDecimal fraction = proration != null ? proration.getFraction() : BigDecimal.ONE;
        boolean fullPeriod = proration == null || proration.isFullPeriod();

        List<PayslipBreakdown.Line> lines = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // ---- 1 & 2. Prorated earnings and gross ----
        BigDecimal proratedBasic = prorate(nz(input.getBasicSalary()), fraction, fullPeriod, scale, rm);
        lines.add(line(PayslipBreakdown.LineType.EARNING, "Basic Salary", proratedBasic));

        BigDecimal proratedTaxableAllow = ZERO;
        BigDecimal proratedNonTaxableAllow = ZERO;
        BigDecimal proratedPensionableAllow = ZERO;

        for (PayrollInput.EarningLine a : input.getAllowances()) {
            BigDecimal full = nz(a.getAmount());
            // Fixed monthly allowances are paid in full when the policy says so, even in a partial month.
            boolean payInFull = a.isFixedMonthly()
                    && prorationPolicy.getFixedAllowanceRule() == FixedAllowanceProrationRule.PAY_IN_FULL;
            BigDecimal amount = (fullPeriod || payInFull)
                    ? scaled(full, scale, rm)
                    : prorate(full, fraction, false, scale, rm);

            lines.add(line(PayslipBreakdown.LineType.EARNING, a.getName(), amount));
            if (a.isTaxable()) {
                proratedTaxableAllow = proratedTaxableAllow.add(amount);
            } else {
                proratedNonTaxableAllow = proratedNonTaxableAllow.add(amount);
            }
            if (a.isPensionable()) {
                proratedPensionableAllow = proratedPensionableAllow.add(amount);
            }
        }

        BigDecimal gross = scaled(proratedBasic.add(proratedTaxableAllow).add(proratedNonTaxableAllow), scale, rm);

        // ---- 3. NSSF employee (Tier I + Tier II) on pensionable pay ----
        BigDecimal pensionablePay;
        if (input.getNssfPensionablePayOverride() != null) {
            BigDecimal override = nz(input.getNssfPensionablePayOverride());
            pensionablePay = (fullPeriod) ? scaled(override, scale, rm)
                    : prorate(override, fraction, false, scale, rm);
        } else {
            pensionablePay = proratedBasic.add(proratedPensionableAllow);
        }

        BigDecimal lel = nz(config.getNssfLowerEarningsLimit());
        BigDecimal uel = nz(config.getNssfUpperEarningsLimit());
        BigDecimal nssfRate = nz(config.getNssfEmployeeRate());
        BigDecimal nssfEmployerRate = nz(config.getNssfEmployerRate());

        BigDecimal tier1Base = min(pensionablePay, lel);
        BigDecimal tier2Base = min(pensionablePay, uel).subtract(lel).max(ZERO);

        BigDecimal nssfTier1Ee = scaled(tier1Base.multiply(nssfRate), scale, rm);
        BigDecimal nssfTier2Ee = scaled(tier2Base.multiply(nssfRate), scale, rm);
        BigDecimal nssfEmployee = nssfTier1Ee.add(nssfTier2Ee);

        BigDecimal nssfTier1Er = scaled(tier1Base.multiply(nssfEmployerRate), scale, rm);
        BigDecimal nssfTier2Er = scaled(tier2Base.multiply(nssfEmployerRate), scale, rm);
        BigDecimal nssfEmployer = nssfTier1Er.add(nssfTier2Er);
        lines.add(line(PayslipBreakdown.LineType.STATUTORY, "NSSF (Employee)", nssfEmployee));

        // ---- 4. SHIF = rate x gross, floored at minimum ----
        BigDecimal shif = scaled(gross.multiply(nz(config.getShifRate())), scale, rm);
        BigDecimal shifMin = nz(config.getShifMinimumContribution());
        if (gross.compareTo(ZERO) > 0 && shif.compareTo(shifMin) < 0) {
            shif = scaled(shifMin, scale, rm);
        }
        if (gross.compareTo(ZERO) == 0) {
            shif = ZERO;
        }
        lines.add(line(PayslipBreakdown.LineType.STATUTORY, "SHIF", shif));

        // ---- 5. AHL employee + employer = rate x gross ----
        BigDecimal ahlEmployee = scaled(gross.multiply(nz(config.getAhlEmployeeRate())), scale, rm);
        BigDecimal ahlEmployer = scaled(gross.multiply(nz(config.getAhlEmployerRate())), scale, rm);
        lines.add(line(PayslipBreakdown.LineType.STATUTORY, "Affordable Housing Levy (Employee)", ahlEmployee));

        // ---- Other deductions (split pre/post tax) ----
        BigDecimal otherPreTax = ZERO;
        BigDecimal otherPostTax = ZERO;
        for (PayrollInput.DeductionLine d : input.getOtherDeductions()) {
            BigDecimal full = nz(d.getAmount());
            // Other recurring deductions follow the earnings proration so a part-month employee
            // isn't charged a full month's SACCO/loan against a part-month salary.
            BigDecimal amount = fullPeriod ? scaled(full, scale, rm) : prorate(full, fraction, false, scale, rm);
            if (d.isPreTax()) {
                otherPreTax = otherPreTax.add(amount);
            } else {
                otherPostTax = otherPostTax.add(amount);
            }
            lines.add(line(PayslipBreakdown.LineType.DEDUCTION, d.getName(), amount));
        }

        // ---- 6. Taxable income ----
        BigDecimal preTaxStatutory = nssfEmployee.add(shif).add(otherPreTax);
        if (config.isAhlPreTaxDeductible()) {
            preTaxStatutory = preTaxStatutory.add(ahlEmployee);
        }
        BigDecimal taxableIncome = gross.subtract(preTaxStatutory).max(ZERO);
        taxableIncome = scaled(taxableIncome, scale, rm);

        // ---- 7. Gross PAYE via graduated bands ----
        BigDecimal grossPaye = computePaye(taxableIncome, config, scale, rm);

        // ---- 8. Reliefs -> PAYE payable ----
        BigDecimal personalRelief = nz(config.getPersonalReliefMonthly());
        // Personal relief only applies when there is tax to relieve / the employee earns.
        if (gross.compareTo(ZERO) == 0) {
            personalRelief = ZERO;
        }
        BigDecimal insuranceRelief = ZERO;
        BigDecimal premium = nz(input.getInsurancePremiumMonthly());
        if (premium.compareTo(ZERO) > 0) {
            insuranceRelief = scaled(premium.multiply(nz(config.getInsuranceReliefRate())), scale, rm);
            BigDecimal cap = nz(config.getInsuranceReliefCap());
            if (insuranceRelief.compareTo(cap) > 0) {
                insuranceRelief = scaled(cap, scale, rm);
            }
        }
        BigDecimal ahlRelief = ZERO;
        if (!config.isAhlPreTaxDeductible() && nz(config.getAhlReliefRate()).compareTo(ZERO) > 0) {
            ahlRelief = scaled(ahlEmployee.multiply(nz(config.getAhlReliefRate())), scale, rm);
        }

        BigDecimal payePayable = grossPaye
                .subtract(personalRelief)
                .subtract(insuranceRelief)
                .subtract(ahlRelief);
        if (payePayable.compareTo(ZERO) < 0) {
            payePayable = ZERO;
        }
        payePayable = scaled(payePayable, scale, rm);
        lines.add(line(PayslipBreakdown.LineType.STATUTORY, "PAYE", payePayable));

        // ---- 9. Net pay ----
        BigDecimal totalEmployeeDeductions = nssfEmployee
                .add(shif).add(ahlEmployee).add(payePayable).add(otherPreTax).add(otherPostTax);
        totalEmployeeDeductions = scaled(totalEmployeeDeductions, scale, rm);

        BigDecimal netPay = scaled(gross.subtract(totalEmployeeDeductions), scale, rm);
        boolean blocking = false;
        if (netPay.compareTo(ZERO) < 0) {
            warnings.add("Net pay is negative (" + netPay + "): statutory and other deductions exceed gross pay");
            blocking = true;
        }
        if (proration != null && proration.isZeroEligible()) {
            warnings.add("Employee has zero eligible days in the period; all earnings prorate to zero");
        }

        // ---- 10. Employer contributions ----
        BigDecimal nita = scaled(nz(config.getNitaAmountPerEmployee()), scale, rm);
        BigDecimal totalEmployerContributions = scaled(nssfEmployer.add(ahlEmployer).add(nita), scale, rm);
        lines.add(line(PayslipBreakdown.LineType.EMPLOYER_CONTRIB, "NSSF (Employer)", nssfEmployer));
        lines.add(line(PayslipBreakdown.LineType.EMPLOYER_CONTRIB, "Affordable Housing Levy (Employer)", ahlEmployer));
        lines.add(line(PayslipBreakdown.LineType.EMPLOYER_CONTRIB, "NITA", nita));

        BigDecimal totalEmployerCost = scaled(gross.add(totalEmployerContributions), scale, rm);

        return PayslipBreakdown.builder()
                .proratedBasic(proratedBasic)
                .proratedTaxableAllowances(proratedTaxableAllow)
                .proratedNonTaxableAllowances(proratedNonTaxableAllow)
                .grossPay(gross)
                .nssfEmployee(nssfEmployee)
                .nssfTier1Employee(nssfTier1Ee)
                .nssfTier2Employee(nssfTier2Ee)
                .shif(shif)
                .ahlEmployee(ahlEmployee)
                .taxableIncome(taxableIncome)
                .grossPaye(grossPaye)
                .personalRelief(personalRelief)
                .insuranceRelief(insuranceRelief)
                .ahlRelief(ahlRelief)
                .payePayable(payePayable)
                .otherPreTaxDeductions(scaled(otherPreTax, scale, rm))
                .otherPostTaxDeductions(scaled(otherPostTax, scale, rm))
                .totalEmployeeDeductions(totalEmployeeDeductions)
                .netPay(netPay)
                .nssfEmployer(nssfEmployer)
                .ahlEmployer(ahlEmployer)
                .nita(nita)
                .totalEmployerContributions(totalEmployerContributions)
                .totalEmployerCost(totalEmployerCost)
                .lines(lines)
                .warnings(warnings)
                .blocking(blocking)
                .build();
    }

    /**
     * Apply the graduated bands to taxable income. Bands are ordered by sequence and treated as a
     * contiguous progressive schedule: each band taxes the slice of income between the previous
     * band's ceiling and this band's {@code upperBound} (the top, open-ended band has a null
     * upper bound). Using the running previous-ceiling as the lower edge — rather than the band's
     * own stored {@code lowerBound} — makes the schedule gap-free regardless of whether the data
     * uses contiguous thresholds (24000 then 24000) or the common "+0.01" convention
     * (24000 then 24000.01). The stored {@code lowerBound} remains available for display.
     */
    private BigDecimal computePaye(BigDecimal taxableIncome, StatutoryConfigSnapshot config,
                                   int scale, RoundingMode rm) {
        if (taxableIncome.compareTo(ZERO) <= 0 || config.getPayeBands() == null) {
            return ZERO;
        }
        List<StatutoryConfigSnapshot.Band> bands = new ArrayList<>(config.getPayeBands());
        bands.sort((a, b) -> Integer.compare(a.getSequence(), b.getSequence()));

        BigDecimal tax = ZERO;
        BigDecimal previousCeiling = ZERO;
        for (StatutoryConfigSnapshot.Band band : bands) {
            BigDecimal upper = band.getUpperBound();
            // The slice this band taxes ends at its upper bound (or all remaining income for the
            // open-ended top band), and starts where the previous band ended.
            BigDecimal bandCeiling = (upper == null) ? taxableIncome : min(taxableIncome, upper);
            BigDecimal taxableInBand = bandCeiling.subtract(previousCeiling);
            if (taxableInBand.compareTo(ZERO) > 0) {
                tax = tax.add(taxableInBand.multiply(nz(band.getRate())));
            }
            if (upper != null) {
                previousCeiling = upper;
            }
            // Stop once we've taxed up to the income (further bands contribute nothing).
            if (upper != null && taxableIncome.compareTo(upper) <= 0) {
                break;
            }
        }
        return scaled(tax, scale, rm);
    }

    // ---- helpers ----

    private static BigDecimal prorate(BigDecimal full, BigDecimal fraction, boolean fullPeriod,
                                      int scale, RoundingMode rm) {
        if (fullPeriod) {
            return scaled(full, scale, rm);
        }
        return scaled(full.multiply(fraction), scale, rm);
    }

    private static BigDecimal scaled(BigDecimal v, int scale, RoundingMode rm) {
        return nz(v).setScale(scale, rm);
    }

    private static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : ZERO;
    }

    private static PayslipBreakdown.Line line(PayslipBreakdown.LineType type, String name, BigDecimal amount) {
        return PayslipBreakdown.Line.builder().lineType(type).name(name).amount(amount).build();
    }
}
