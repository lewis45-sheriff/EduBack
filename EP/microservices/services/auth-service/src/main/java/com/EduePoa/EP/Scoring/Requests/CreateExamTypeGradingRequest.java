package com.EduePoa.EP.Scoring.Requests;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateExamTypeGradingRequest {
//    @NotNull(message = "The exam type is required.")
//    private Long examTypeId;

    @NotNull(message = "The Min score is required.")
    private Double min;

    @NotNull(message = "The Maximum range score is required.")
    private Double max;

    private Double point;

    @NotNull(message = "Grading for the given range is required.")
    private String grading;
}
