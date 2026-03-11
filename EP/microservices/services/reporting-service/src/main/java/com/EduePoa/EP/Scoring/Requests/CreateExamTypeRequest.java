package com.EduePoa.EP.Scoring.Requests;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateExamTypeRequest {
    @NotNull(message = "Exam type name is required.")
    private String name;

    @NotNull(message = "Exam max score is required.")
    private Long maxScore;

    @NotNull(message = "Exam min score is required.")
    private Long minScore;
}
