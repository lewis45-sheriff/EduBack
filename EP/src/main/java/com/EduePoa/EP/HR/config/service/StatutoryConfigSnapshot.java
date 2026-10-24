package com.EduePoa.EP.HR.config.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * An immutable, fully-resolved snapshot of the statutory configuration effective for a
 * single payroll run's period. This is a plain value object (not a JPA entity): it is
 * computed once by {@link StatutoryConfigService#resolveFor} and then serialized to JSON
 * and stored on the {@code PayrollRun} so the run remains exactly reproducible even after
 * rates later change. The deterministic calculation engine consumes <em>only</em> this
 * snapshot — never the live config tables — so no later edit can alter a processed run.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatutoryConfigSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The date the snapshot was resolved for (first day of the payroll month). */
    private LocalDate asOf;

    // ---- PAYE ----
    private List<Band> payeBands;
    private BigDecimal personalReliefMonthly;
    private BigDecimal insuranceReliefRate;
    private BigDecimal insuranceReliefCap;

    // ---- NSSF ----
    private BigDecimal nssfLowerEarningsLimit;
    private BigDecimal nssfUpperEarningsLimit;
    private BigDecimal nssfEmployeeRate;
    private BigDecimal nssfEmployerRate;

    // ---- SHIF ----
    private BigDecimal shifRate;
    private BigDecimal shifMinimumContribution;

    // ---- AHL ----
    private BigDecimal ahlEmployeeRate;
    private BigDecimal ahlEmployerRate;
    private boolean ahlPreTaxDeductible;
    private BigDecimal ahlReliefRate;

    // ---- NITA ----
    private BigDecimal nitaAmountPerEmployee;

    /** One graduated PAYE band; {@code upperBound == null} is the open-ended top band. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Band implements Serializable {
        private static final long serialVersionUID = 1L;
        private int sequence;
        private BigDecimal lowerBound;
        private BigDecimal upperBound;
        private BigDecimal rate;
    }
}
