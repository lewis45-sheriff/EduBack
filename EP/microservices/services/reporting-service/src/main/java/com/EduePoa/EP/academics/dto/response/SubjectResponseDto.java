package com.EduePoa.EP.academics.dto.response;

import lombok.Data;

@Data
public class SubjectResponseDto {
    private Long id;
    private String subjectName;
    private String subjectCode;
    private String learningArea;
    private Boolean isCbcCore;
}
