package com.EduePoa.EP.Scoring.Response;

import lombok.Data;

@Data
public class SubjectMarksResponse {
    private String firstName;
    private String LastName;
    private String subjectName;
    private Double marks;
    private String gradeName;
}
