package com.EduePoa.EP.academics.assessment.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pure, reproducible numeric calculations for assessments. No thresholds and no framework lookups
 * live here — this only turns raw scores into a normalized percentage and combines weighted
 * components. Performance-level resolution is delegated to {@link PerformanceLevelService}.
 */
public interface AssessmentCalculationService {

    /**
     * Normalize a raw score to a percentage in [0,100]: {@code raw / max * 100}.
     * Returns null if inputs are missing; throws if max &le; 0 or raw &gt; max.
     */
    BigDecimal normalize(BigDecimal rawScore, BigDecimal maximumScore);

    /**
     * Combine weighted component percentages. Each entry is (normalizedPercentage, weight); the
     * result is the weight-weighted average: {@code sum(pct*weight) / sum(weight)}. Weights may be
     * fractions or percentages — only their ratios matter. Returns null for an empty list.
     */
    BigDecimal weightedAverage(List<ComponentScore> components);

    /** Simple mean of a list of percentages (used when a framework defines no component weights). */
    BigDecimal average(List<BigDecimal> percentages);

    /** A normalized component percentage paired with its configured weight. */
    record ComponentScore(BigDecimal percentage, BigDecimal weight) {}
}
