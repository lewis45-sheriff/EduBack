package com.EduePoa.EP.academics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single student's attendance state for a given date within a class roster.
 * <p>
 * {@code attendanceId} and {@code status} are null when the student has not
 * yet been marked for that date, allowing the marking screen to pre-fill the
 * roster with unmarked students.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentAttendanceRowDto {
    private Long studentId;
    private String studentName;
    private String admissionNumber;
    private Long attendanceId;
    private String status;
    private String checkInTime;
    private String remarks;
    private boolean marked;
}
