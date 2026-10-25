package com.EduePoa.EP.academics.dto.response;

import lombok.Data;

/**
 * A teacher's own subject assignment, enriched with the information needed to open the assignment
 * and enter marks (subject/grade/stream names, term, year, max score and current assessment status).
 * Derived from {@link com.EduePoa.EP.academics.entity.TeacherSubjectAssignment}; returned only for
 * the authenticated teacher.
 */
@Data
public class TeacherAssignmentDto {
    private Long assignmentId;
    private Long gradeId;
    private String gradeName;
    private Long streamId;      // null for whole-grade assignments
    private String streamName;  // null for whole-grade assignments
    private Long subjectId;
    private String learningAreaName;
    private Integer year;
    private Long termId;        // null when term-agnostic
    private String termName;    // null when term-agnostic
    private Long examTypeId;    // null until an exam type is selected for entry
    private String examTypeName;
    /** DRAFT | SUBMITTED | APPROVED | PUBLISHED | AMENDED (DRAFT when nothing saved yet). */
    private String assessmentStatus;
    /** Max attainable score for the selected exam type, when known. */
    private Integer maxScore;
}
