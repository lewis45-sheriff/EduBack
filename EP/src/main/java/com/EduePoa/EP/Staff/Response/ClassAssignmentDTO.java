package com.EduePoa.EP.Staff.Response;

import lombok.Builder;
import lombok.Data;

/**
 * The class a staff member is the class teacher of. Mirrors the frontend's
 * AssignedClass shape so the teacher attendance page can consume it directly.
 */
@Data
@Builder
public class ClassAssignmentDTO {
    /** "stream" when the class is a specific stream, "grade" for an unstreamed grade. */
    private String type;
    /** Stream id when type=stream, or grade id when type=grade. Used to fetch students. */
    private Long id;
    /** Always the grade id — used for the markAttendance payload. */
    private Long gradeId;
    private String gradeName;
    /** Stream name, or null for an unstreamed grade. */
    private String streamName;
    /** Ready-to-display label, e.g. "Grade 5 — West" or "Grade 5". */
    private String label;
}
