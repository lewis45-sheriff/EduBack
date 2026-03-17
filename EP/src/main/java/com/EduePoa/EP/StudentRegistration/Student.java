package com.EduePoa.EP.StudentRegistration;

import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.FeeStructure.FeeStructure;
import com.EduePoa.EP.Grade.Grade;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.Year;

@Data
@Entity
@RequiredArgsConstructor
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String admissionNumber;

    private String firstName;
    private String middleName;
    private String lastName;
    private LocalDate dateOfBirth;
    private LocalDate admissionDate;
    private String gradeName;
    private String streamName;
    private String gender;

    private char is_lockedFlag = 'N';

    @Enumerated(EnumType.STRING)
    private Status status;

    @Enumerated(EnumType.STRING)
    private BoardingStatus boardingStatus;

    private String routeName;
    private String residentialAddress;

    @Column(columnDefinition = "TEXT")
    private String medicalNotes;

    private Boolean specialNeedsFlag = false;

    @Column(columnDefinition = "TEXT")
    private String specialNeedsNotes;

    private String previousSchoolName;
    private String previousSchoolNemisCode;
    private String birthCertificateNumber;
    private String nationality;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "fee_structure_id", referencedColumnName = "id")
    private FeeStructure feeStructure;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "grade_id", referencedColumnName = "id")
    private Grade grade;

    @Column(name = "on_last_grade", nullable = false)
    @JsonIgnore
    private char onLastGrade = 'N';

    @Lob
    private String studentImage;

    @Column(nullable = false)
    private Year academicYear = Year.of(LocalDate.now().getYear());

    @Column(nullable = false)
    private Boolean isDeleted = false;
}
