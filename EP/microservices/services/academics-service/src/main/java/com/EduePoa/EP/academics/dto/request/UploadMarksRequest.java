package com.EduePoa.EP.academics.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class UploadMarksRequest {
    private Long gradeId;
    private Long subjectId;
    private Long termId;
    private Integer year;

    private List<StudentMarkEntry> marks;

    @Data
    public static class StudentMarkEntry {
        private Long studentId;
        private Long examTypeId;
        private Double score;
    }
}
