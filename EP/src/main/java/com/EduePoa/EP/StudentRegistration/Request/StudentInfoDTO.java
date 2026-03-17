package com.EduePoa.EP.StudentRegistration.Request;

import com.EduePoa.EP.StudentRegistration.BoardingStatus;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StudentInfoDTO {
    private String admissionNumber;
    private String firstName;
    private String middleName;
    private String lastName;
    private LocalDate dateOfBirth;
    private LocalDate admissionDate;
    private Long gradeId;
    private String streamName;
    private String gender;
    private String studentImage;
    private String status;

    private BoardingStatus boardingStatus;
    private String routeName;
    private String residentialAddress;
    private String medicalNotes;
    private Boolean specialNeedsFlag = false;
    private String specialNeedsNotes;
    private String previousSchoolName;
    private String previousSchoolNemisCode;
    private String birthCertificateNumber;
    private String nationality;
}
