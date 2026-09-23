package com.EduePoa.EP.academics.assessment.service;

import com.EduePoa.EP.academics.assessment.entity.AssessmentFramework;
import com.EduePoa.EP.academics.assessment.entity.PerformanceLevel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Resolves and validates configurable performance levels. There is intentionally no hard-coded
 * threshold anywhere in the implementation — all resolution reads {@link PerformanceLevel} rows.
 */
public interface PerformanceLevelService {

    /** Resolve the performance level whose band contains the given percentage, for a framework. */
    Optional<PerformanceLevel> resolve(AssessmentFramework framework, BigDecimal percentage);

    /**
     * Validate a set of bands: each has minScore &lt;= maxScore, bounds within [0,100], and no two
     * bands overlap. Throws IllegalArgumentException describing the first violation found.
     */
    void validateBands(List<PerformanceLevel> levels);
}
