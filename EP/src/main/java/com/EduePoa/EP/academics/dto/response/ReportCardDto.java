package com.EduePoa.EP.academics.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured, curriculum-stage-aware report card. Designed so a PDF renderer can be added later
 * without changing the assembly logic. Fields a given stage does not use are simply left null/empty
 * — the frontend decides what to render. No JPA entity is exposed directly.
 */
@Data
public class ReportCardDto {

    // Header
    private String schoolName;
    private Integer academicYear;
    private String termName;
    private Long studentId;
    private String studentName;
    private String admissionNumber;
    private String gradeName;
    private String streamName;

    // Sections
    /** Overall term result per learning area (aggregated from CbcGradeResult). */
    private List<LearningAreaResult> learningAreas = new ArrayList<>();
    /**
     * Every individual assessment/exam recorded for the learner this term (from StudentsScore),
     * one row per assessment per learning area. Distinct from {@link #learningAreas}, which is the
     * aggregated overall result — these are the raw underlying assessments and are never
     * double-counted against the overall.
     */
    private List<IndividualAssessment> individualAssessments = new ArrayList<>();
    private List<CompetencyResult> competencies = new ArrayList<>();
    /**
     * True when at least one competency evidence record exists for this learner/term. When false,
     * the report shows a clear "not yet assessed" state rather than fabricating competency results.
     */
    private boolean competenciesAssessed;
    private List<ValueObservation> values = new ArrayList<>();
    /**
     * The school's CBC core values catalogue for the active curriculum (names only). These are the
     * values the school promotes; they are NOT per-learner achievements. Per-learner value
     * observations are not yet captured, so {@link #values} stays empty rather than fabricating.
     */
    private List<String> valueCatalogue = new ArrayList<>();
    private AttendanceSummary attendance;

    // Overall
    private BigDecimal overallPercentage;      // null when framework does not aggregate
    private String overallPerformanceLabel;
    private Integer position;                  // null unless a ranking policy is enabled
    private String rankingScope;               // e.g. WITHIN_STREAM; null when ranking disabled
    private String teacherOverallComment;
    private String headteacherComment;

    private String assessmentFrameworkCode;
    private String curriculumVersionCode;

    // Exam-column totals across all learning areas (null when the per-exam marks are absent).
    // These mirror the "TOTAL" row of a printed report form.
    private BigDecimal openerTotal;
    private BigDecimal midtermTotal;
    private BigDecimal endtermTotal;
    private BigDecimal averageTotal;           // sum of per-subject averages

    // Fees snapshot for the term/year, sourced from the Finance module. Null when no finance
    // record exists for the student/term/year.
    private FeesSummary fees;

    @Data
    public static class FeesSummary {
        private BigDecimal totalFeeAmount;
        private BigDecimal paidAmount;
        private BigDecimal balance;            // > 0 means arrears outstanding
        private boolean cleared;               // true when balance <= 0
        private String status;                 // CLEARED | OUTSTANDING | OVERPAID
    }

    @Data
    public static class LearningAreaResult {
        private Long learningAreaId;
        private String learningAreaName;
        private BigDecimal normalizedScore;
        private String performanceLevelCode;   // e.g. ME1
        private String performanceLevelLabel;  // e.g. Meeting Expectation
        private Integer performancePoints;      // e.g. 6 (KJSEA)
        private String teacherComment;
        private String teacherName;

        // Raw per-exam marks for this learning area (each out of its exam's max, typically 100).
        // Null when that exam has not been recorded — never defaulted to 0 so the report can
        // distinguish "not sat" from an actual zero.
        private BigDecimal openerScore;
        private BigDecimal midtermScore;
        private BigDecimal endtermScore;
        private BigDecimal averageScore;        // mean of the available exam marks above
    }

    /**
     * A single assessment/exam recorded for the learner in the term (from StudentsScore). The
     * "assessmentName" is the exam type (e.g. Opener, Mid-Term, End-Term). Score/maxScore/percentage
     * reflect that one assessment only — these rows are the evidence behind the overall aggregate
     * and must not be summed into the overall result.
     */
    @Data
    public static class IndividualAssessment {
        private Long learningAreaId;
        private String learningAreaName;
        private String assessmentName;      // exam type name
        private BigDecimal score;           // raw mark obtained
        private BigDecimal maxScore;        // exam maximum (e.g. 100)
        private BigDecimal percentage;      // score / maxScore * 100, rounded
        private String assessmentDate;      // ISO date when available, else null
    }

    @Data
    public static class CompetencyResult {
        private Long competencyId;
        private String competencyName;
        private String levelLabel;
        private String comment;      // evidence / teacher comment
    }

    @Data
    public static class ValueObservation {
        private Long valueId;
        private String valueName;
        private String observation;  // evidence / comment
    }

    @Data
    public static class AttendanceSummary {
        private Integer daysPresent;
        private Integer daysAbsent;
        private Integer daysLate;
        private Integer daysExcused;
    }
}
