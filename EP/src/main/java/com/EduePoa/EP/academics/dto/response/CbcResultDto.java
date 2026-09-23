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
    private Integer cbcLevel; // 1-4 broad level (legacy-compatible)
    private String cbcLabel; // Below/Approaching/Meeting/Exceeding Expectation

    // Framework-driven fields (populated when an assessment framework resolved the result)
    private java.math.BigDecimal normalizedScore;
    private String performanceLevelCode;   // e.g. ME1
    private String performanceLevelLabel;  // e.g. Meeting Expectation
    private String performanceAbbreviation; // e.g. ME
    private Integer performancePoints;      // e.g. 6 (KJSEA)
    private String assessmentFrameworkCode; // e.g. KJSEA
    private String teacherComment;
}
