package com.EduePoa.EP.academics.dto.request;

import lombok.Data;

import java.util.List;

/**
 * Assign subjects (learning areas) to a grade for a year.
 * <p>
 * {@code subjectIds} controls the mode:
 * <ul>
 *   <li>a single id  &rarr; assign one subject at a time;</li>
 *   <li>several ids  &rarr; assign a chosen set (multi-select);</li>
 *   <li>null / empty &rarr; assign ALL subjects in the system (backward-compatible default).</li>
 * </ul>
 * Each id is an {@code AcademicSubject} id (obtainable from a learning area's {@code academicSubjectId}).
 */
@Data
public class AssignSubjectsToGradeRequest {
    private Long gradeId;
    private Integer year;
    private List<Long> subjectIds;
}
