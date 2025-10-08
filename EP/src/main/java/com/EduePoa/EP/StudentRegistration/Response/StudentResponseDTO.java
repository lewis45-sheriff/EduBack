package com.EduePoa.EP.StudentRegistration.Response;

import lombok.Data;

import java.time.LocalDate;
@Data
public class StudentResponseDTO {
    private Long id;
    private String admissionNumber;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String admissionDate;
    private String gradeName;
    private String gender;
    private String studentImage;
    private String status;
}
