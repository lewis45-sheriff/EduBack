package com.EduePoa.EP.academics.assessment.dto;

import lombok.Data;

/**
 * Request to record (or clear) the class teacher's remark shown on a learner's CBC report card
 * for a given term/year.
 *
 * <p>The remark is a single per-learner, per-term free-text comment. It is persisted onto the
 * learner's {@code cbc_grade_result} rows (the same source the report reads), and survives a
 * results re-compute because the compute flow never overwrites {@code teacher_comment}.
 *
 * <p>A {@code null} or blank {@link #remark} clears any existing remark for that term/year.
 */
@Data
public class RecordTeacherRemarkRequest {

    /** The learner the remark is about. */
    private Long studentId;

    /** 1 = TERM_1, 2 = TERM_2, 3 = TERM_3. */
    private Integer termId;

    private Integer year;

    /** The class teacher's remark. Null/blank clears the existing remark. */
    private String remark;
}
