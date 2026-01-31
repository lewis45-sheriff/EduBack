package com.EduePoa.EP.Academics.Attendance.Requests;

import lombok.Data;

@Data
public class StudentAttendanceRequestDTO {

    private Long studentId;
    private String status;   // PRESENT, ABSENT, LATE, EXCUSED
    private String remarks;
}