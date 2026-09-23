package com.EduePoa.EP.academics.dto.request;

import lombok.Data;

import java.util.List;

/**
 * Assign one teacher to many subject/class combinations in a single call. A teacher can teach
 * multiple subjects across different grades/streams, so each entry is an independent assignment.
 */
@Data
public class BulkAssignTeacherRequest {
    private Long teacherId;
    private Integer year;
    private List<Item> assignments;

    @Data
    public static class Item {
        private Long academicSubjectId;
        private Long gradeId;
        private Long streamId;  // optional
        private Integer termId; // optional: 1|2|3
    }
}
