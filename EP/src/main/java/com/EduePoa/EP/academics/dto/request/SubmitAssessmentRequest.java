package com.EduePoa.EP.academics.dto.request;

import lombok.Data;

/**
 * Teacher-portal request to submit an assessment for approval. The assignment is identified by the
 * path variable; this body carries the exact assessment coordinates (term/year/exam type) so the
 * server marks the correct set of {@code StudentsScore} rows as SUBMITTED.
 */
@Data
public class SubmitAssessmentRequest {
    private Long termId;
    private Integer year;
    private Long examTypeId;
}
