package com.EduePoa.EP.StudentRegistration.Request;

import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

@Data
public class StudentRequestDTO {
    private Long id;
    private String admissionNumber;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String admissionDate;
    private Long grade;
    private String gender;
    private String studentImage;
}
