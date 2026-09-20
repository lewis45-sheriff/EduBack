package com.EduePoa.EP.academics.dto.request;

import com.EduePoa.EP.Authentication.Enum.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-student entry within a bulk attendance submission.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentAttendanceRequestDTO {
    private Long studentId;
    private AttendanceStatus status;
    private String remarks;
}
