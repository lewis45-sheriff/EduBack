package com.EduePoa.EP.academics.dto.request;

import lombok.Data;

@Data
public class AssignSubjectsToGradeRequest {
    private Long gradeId;
    private Integer year;
}
