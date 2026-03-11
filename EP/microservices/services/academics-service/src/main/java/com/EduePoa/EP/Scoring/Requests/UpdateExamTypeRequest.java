package com.EduePoa.EP.Scoring.Requests;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateExamTypeRequest {
    @NotNull(message = "Exam type id is required.")
    private Long id;

    @NotNull(message = "Exam type name is required.")
    private String name;

    @NotNull(message = "Exam type max score is required.")
    private  Long maxScore;

    @NotNull(message = "Exam type min score is required.")
    private Long minScore;
}
