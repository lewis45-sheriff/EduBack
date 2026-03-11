package com.EduePoa.EP.StudentRegistration.Response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StudentsPerGradeDTO {
    private String grade;
    private Long students;
}