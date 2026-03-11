package com.EduePoa.EP.academics.dto.response;

import lombok.Data;

@Data
public class CbcResultDto {
    private Long id;
    private Long studentId;
    private String studentName;
    private Long subjectId;
    private String subjectName;
    private String termName;
    private Integer year;
    private Double averageScore;
    private Integer cbcLevel; // 1-4
    private String cbcLabel; // BE / AE / ME / EE
}
