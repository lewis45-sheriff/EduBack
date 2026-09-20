package com.EduePoa.EP.academics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response shape consumed by the frontend {@code AttendanceRecord} type.
 * <p>
 * {@code date} and {@code checkInTime} are serialized as strings so the
 * frontend can parse them directly. {@code status} is the enum name
 * (PRESENT | ABSENT | LATE | EXCUSED).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRecordDto {
    private Long id;
    private Long studentId;
    private String studentName;
    private String admissionNumber;
    private String date;
    private String status;
    private String checkInTime;
    private String remarks;
    private String markedBy;
}
