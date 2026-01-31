package com.EduePoa.EP.Academics.Attendance.Responses;


import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AttendanceResponseDTO {

    private Long id;
    private Long gradeId;
    private String gradeName;
    private LocalDateTime dateTime;
    private List<StudentAttendanceResponseDTO> students;
}