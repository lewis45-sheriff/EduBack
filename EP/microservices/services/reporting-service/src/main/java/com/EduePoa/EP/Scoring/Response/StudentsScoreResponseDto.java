package com.EduePoa.EP.Scoring.Response;

import lombok.Data;

import java.time.Year;

@Data
public class StudentsScoreResponseDto {
    private Long id;
    private String studentName;
    private String gradeName;
    private String termName;
    private String subjectTeacherName;
    private Double examScore;
    private String examType;
    private Year year;
    private boolean resultApproved;
    private String resultApprovedBy;
}