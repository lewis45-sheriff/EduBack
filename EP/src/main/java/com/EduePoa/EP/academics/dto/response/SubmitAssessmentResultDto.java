package com.EduePoa.EP.academics.dto.response;

import lombok.Data;

/**
 * Result of submitting an assessment: the resulting status and how many score rows transitioned.
 */
@Data
public class SubmitAssessmentResultDto {
    private String assessmentStatus;
    private int submittedCount;
}
