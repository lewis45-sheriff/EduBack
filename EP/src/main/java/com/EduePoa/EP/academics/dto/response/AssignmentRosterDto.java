package com.EduePoa.EP.academics.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The learner roster for one assignment + exam type + term + year, with existing marks prefilled.
 * Learners without a saved score return {@code rawScore == null} (blank), never 0.
 */
@Data
public class AssignmentRosterDto {
    private Long assignmentId;
    private Long subjectId;
    private String learningAreaName;
    private Long gradeId;
    private String gradeName;
    private Long streamId;
    private String streamName;
    private Long termId;
    private String termName;
    private Integer year;
    private Long examTypeId;
    private String examTypeName;
    private Integer maxScore;
    /** DRAFT | SUBMITTED | APPROVED | PUBLISHED | AMENDED. */
    private String assessmentStatus;
    private List<RosterLearner> learners = new ArrayList<>();

    @Data
    public static class RosterLearner {
        private Long studentId;
        private String studentName;
        private String admissionNumber;
        /** Existing mark, or null when not yet entered. */
        private BigDecimal rawScore;
        private Integer maxScore;
        /** Per-learner status mirroring the saved score row (DRAFT when none). */
        private String status;
    }
}
