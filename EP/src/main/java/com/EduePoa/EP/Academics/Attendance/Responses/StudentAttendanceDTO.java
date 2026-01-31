package com.EduePoa.EP.Academics.Attendance.Responses;

import lombok.Data;

@Data
public class StudentAttendanceDTO {

    private Long id;
    private String firstName;
    private String lastName;
    private String admissionNumber;
}