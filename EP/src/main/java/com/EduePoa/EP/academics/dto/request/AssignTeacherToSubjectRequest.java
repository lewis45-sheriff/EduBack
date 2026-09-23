package com.EduePoa.EP.academics.dto.request;

import lombok.Data;

/**
 * Request to assign a teacher to a learning area for a grade (optionally a stream and term) in a
 * given academic year. streamId and termId are optional.
 */
@Data
public class AssignTeacherToSubjectRequest {
    private Long teacherId;
    private Long academicSubjectId;
    private Long gradeId;
    private Long streamId;   // optional
    private Integer termId;  // optional: 1|2|3
    private Integer year;
}
