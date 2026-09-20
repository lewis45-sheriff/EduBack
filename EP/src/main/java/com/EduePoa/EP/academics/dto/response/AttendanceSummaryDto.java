package com.EduePoa.EP.academics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-student attendance tallies over a date range, for admin reporting.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSummaryDto {
    private Long studentId;
    private String studentName;
    private String admissionNumber;
    private long present;
    private long absent;
    private long late;
    private long excused;
    private long total;
}
