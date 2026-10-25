package com.EduePoa.EP.academics.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A single learner's results across all exams for an academic year, grouped by term. Assembled by
 * reusing the existing report-card logic ({@link ReportCardDto}) per term, so every value is a real
 * computed/recorded figure — nothing is estimated. A term with no recorded exams returns empty
 * lists and a null overall rather than fabricated marks.
 */
@Data
public class StudentResultsDto {
    private Long studentId;
    private String studentName;
    private String admissionNumber;
    private String gradeName;
    private String streamName;
    private Integer year;

    /** One block per term that has any recorded data, in term order. */
    private List<TermResults> terms = new ArrayList<>();

    /**
     * Chronological performance trend across every term/year the learner has a computed summary,
     * spanning all grades they have been in. Backs the performance graph. Ordered oldest-first.
     */
    private List<TrendPoint> trend = new ArrayList<>();

    @Data
    public static class TrendPoint {
        private Integer year;
        private Long termId;
        private String termName;
        private String gradeName;
        /** "Grade 1 · Term 3" style label for the x-axis. */
        private String label;
        /** Overall aggregate percentage, or null when the framework does not aggregate. */
        private BigDecimal overallPercentage;
        private String overallPerformanceLabel;
        private Integer position;
    }

    @Data
    public static class TermResults {
        private Long termId;
        private String termName;

        /** Overall term aggregate (null when the framework does not aggregate or nothing computed). */
        private BigDecimal overallPercentage;
        private String overallPerformanceLabel;
        private Integer position;
        private String rankingScope;

        /** Per-subject aggregated result with the opener/midterm/endterm columns. */
        private List<ReportCardDto.LearningAreaResult> learningAreas = new ArrayList<>();

        /** Every individual exam recorded this term (one row per exam type per subject). */
        private List<ReportCardDto.IndividualAssessment> exams = new ArrayList<>();

        /** True when any exam mark or computed result exists for the learner this term. */
        private boolean hasData;
    }
}
