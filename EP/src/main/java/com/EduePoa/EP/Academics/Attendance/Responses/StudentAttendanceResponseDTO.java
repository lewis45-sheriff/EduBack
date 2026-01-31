package com.EduePoa.EP.Academics.Attendance.Responses;
import lombok.Data;

@Data
public class StudentAttendanceResponseDTO {

    private Long studentId;
    private String studentName;
    private String status;
    private String remarks;
}