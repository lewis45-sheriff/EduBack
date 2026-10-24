package com.EduePoa.EP.HR.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The full computed breakdown for one employee's payslip, as produced by the pure
 * {@link PayrollCalculationService}. Every figure is already rounded to money scale. Employee
 * deductions, employer contributions and the itemised lines are kept separate so the payroll
 * register and statutory summaries can reconcile and so employer cost is never confused with
 * employee deductions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayslipBreakdown {

    // ---- Earnings ----
    private BigDecimal proratedBasic;
    private BigDecimal proratedTaxableAllowances;
    private BigDecimal proratedNonTaxableAllowances;
    private BigDecimal grossPay;

    // ---- Statutory employee deductions ----
    private BigDecimal nssfEmployee;
    private BigDecimal nssfTier1Employee;
    private BigDecimal nssfTier2Employee;
    private BigDecimal shif;
    private BigDecimal ahlEmployee;

    // ---- PAYE ----
    private BigDecimal taxableIncome;
    private BigDecimal grossPaye;
    private BigDecimal personalRelief;
    private BigDecimal insuranceRelief;
    private BigDecimal ahlRelief;
    private BigDecimal payePayable;

    // ---- Other deductions ----
    private BigDecimal otherPreTaxDeductions;
    private BigDecimal otherPostTaxDeductions;

    // ---- Net ----
    private BigDecimal totalEmployeeDeductions;
    private BigDecimal netPay;

    // ---- Employer contributions (cost, not deducted from employee) ----
    private BigDecimal nssfEmployer;
    private BigDecimal ahlEmployer;
    private BigDecimal nita;
    private BigDecimal totalEmployerContributions;

    /** Total employer cost = gross + employer contributions. */
    private BigDecimal totalEmployerCost;

    /** Itemised lines for display/audit (earnings, statutory, deductions, employer contributions). */
    @Builder.Default
    private List<Line> lines = new ArrayList<>();

    /**
     * Non-fatal validation messages surfaced to the processor (e.g. zero-eligible days, net floored).
     * A non-empty list does not necessarily block approval unless {@link #blocking} is set.
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /** True when a blocking condition was detected (e.g. deductions exceed gross). */
    @Builder.Default
    private boolean blocking = false;

    public enum LineType { EARNING, STATUTORY, DEDUCTION, EMPLOYER_CONTRIB }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Line {
        private LineType lineType;
        private String name;
        private BigDecimal amount;
    }
}
