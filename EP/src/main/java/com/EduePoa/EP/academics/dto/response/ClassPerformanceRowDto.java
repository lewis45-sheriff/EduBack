package com.EduePoa.EP.academics.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * One learner's row in the class-performance table. Values come from the existing computed
 * {@link com.EduePoa.EP.academics.assessment.entity.StudentTermSummary} and the existing
 * {@link com.EduePoa.EP.academics.assessment.service.RankingService}; nothing is fabricated.
 * A learner with no computed summary returns null aggregate/level and {@code computed == false}.
 */
@Data
public class ClassPerformanceRowDto {
    private Long studentId;
    private String studentName;
    private String admissionNumber;
    /** Overall aggregate percentage, or null when not computed / the framework does not aggregate. */
    private BigDecimal overallPercentage;
    /** Broad performance/competency label, or null when not computed. */
    private String performanceLevel;
    /** Class position, or null when ranking is disabled or no aggregate exists. */
    private Integer position;
    /** Scope label for the position (e.g. WITHIN_STREAM), or null. */
    private String rankingScope;
    /** True when a computed term summary exists for this learner. */
    private boolean computed;
}
