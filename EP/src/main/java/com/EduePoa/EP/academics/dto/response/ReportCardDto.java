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
    private List<LearningAreaResult> learningAreas = new ArrayList<>();
    private List<CompetencyResult> competencies = new ArrayList<>();
    private List<ValueObservation> values = new ArrayList<>();
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
