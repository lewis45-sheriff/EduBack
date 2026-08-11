package com.EduePoa.EP.Scoring.Requests;

import lombok.Data;

import java.util.List;
@Data
public class StudentBulkRequest {
    private int year;
    private Long grade;
    private Long subject;
    private List<StudentMark> marks;
    @Data
    public static class StudentMark {
        private Long studentId;
        private Long termId;
        private Long examTypeId;
        private Double mark;
        private Double marks; 

    }
}

