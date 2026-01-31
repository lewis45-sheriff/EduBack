package com.EduePoa.EP.Academics.Attendance.Requests;

import lombok.Data;

import java.util.List;

@Data
public class AttendanceRequestDTO {

    private Long gradeId;
    private List<StudentAttendanceRequestDTO> students;
}