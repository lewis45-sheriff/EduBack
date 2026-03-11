package com.EduePoa.EP.Scoring.Response;

import lombok.Data;

@Data
public class ExamTypeGradingResponseDto {
    private Long id;
    private Double min;
    private Double max;
    private Double point;
    private String grading;
}
