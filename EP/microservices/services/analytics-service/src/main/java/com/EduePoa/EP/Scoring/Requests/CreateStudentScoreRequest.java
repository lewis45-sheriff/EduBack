package com.EduePoa.EP.Scoring.Requests;

import lombok.Data;

@Data
public class CreateStudentScoreRequest {
    private Long studentId;      // Student ID to associate the result
    private Long gradeId;        // Grade ID
    private Long termId;         // Term ID
    private Long subjectId;         // Subject ID
    private Long subjectTeacherId; // Subject Teacher (User ID)
    private Double examScore;    // Exam score
    private Long examTypeId;     // Exam Type (e.g., Midterm, Final)
}
