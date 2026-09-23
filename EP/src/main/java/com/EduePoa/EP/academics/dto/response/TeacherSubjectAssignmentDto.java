package com.EduePoa.EP.academics.dto.response;

import lombok.Data;

@Data
public class TeacherSubjectAssignmentDto {
    private Long id;
    private Long teacherId;
    private String teacherName;
    private Long academicSubjectId;
    private String subjectName;
    private Long gradeId;
    private String gradeName;
    private Long streamId;
    private String streamName;
    private String termName; // null when term-agnostic
    private Integer year;
    private Boolean active;
}
