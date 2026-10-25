package com.EduePoa.EP.academics.dto.response;

import lombok.Data;

/**
 * A class (grade, optionally a stream) for which the authenticated teacher is the class teacher.
 * Backs the teacher's "My Class" view and the navigation to attendance, computation, report forms
 * and class performance. Resolved server-side from the User -&gt; Staff -&gt; classTeacher relationship.
 */
@Data
public class TeacherClassDto {
    private Long gradeId;
    private String gradeName;
    private Long streamId;      // null when the teacher is class teacher of a whole grade
    private String streamName;  // null when the teacher is class teacher of a whole grade
    /** Count of active (non-deleted) learners in the class. */
    private int learnerCount;
}
