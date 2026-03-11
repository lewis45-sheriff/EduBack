package com.EduePoa.EP.academics.dto.request;

import lombok.Data;

@Data
public class CreateSubjectRequest {
    private String subjectName;
    private String subjectCode;
    private String learningArea;
    private Boolean isCbcCore = false;
}
