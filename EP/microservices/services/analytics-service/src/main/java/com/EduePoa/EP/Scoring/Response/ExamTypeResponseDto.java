package com.EduePoa.EP.Scoring.Response;

import lombok.Data;

@Data
public class ExamTypeResponseDto {
    private Long id;
    private String name;
    private  Long maxScore;
    private Long minScore;
}