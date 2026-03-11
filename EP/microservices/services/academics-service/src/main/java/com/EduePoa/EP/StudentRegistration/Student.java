package com.EduePoa.EP.StudentRegistration;

import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.Authentication.User.User;
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
    private String lastName;
    private LocalDate dateOfBirth;
    private LocalDate admissionDate;
    private String gradeName;
    private String gender;
    private  char is_lockedFlag = 'N';
    @Enumerated(EnumType.STRING)
    private Status status;
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "parent_id", referencedColumnName = "id")
    private User parent;
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "fee_structure_id", referencedColumnName = "id")
    private FeeStructure feeStructure;
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "grade_id", referencedColumnName = "id")
    private Grade grade;
//
//
//
//    @Column(nullable = false)
//    private String gender;
//
//
//
//    @Column(name = "is_new_student", nullable = false)
//    @JsonIgnore
//    private char isNewStudent = 'Y'; // Default value 'Y' for any student created
//
    @Column(name = "on_last_grade", nullable = false)
    @JsonIgnore
    private char onLastGrade = 'N'; // Default value 'N' for any student created
//
//    @Column(name = "completed_last_grade", nullable = false)
//    @JsonIgnore
//    private char completedLastGrade = 'N'; // Default value 'N' for any student created
//
//    @JsonIgnore
//    private LocalDate updatedAt;
    @Lob
    private String studentImage;
    @Column(nullable = false)
    private Year academicYear = Year.of(LocalDate.now().getYear());
//    @Column(name = "is_approved", nullable = false)
//    private char isApproved = 'N'; // 'Y' = approved, 'N' = not approved
//
//    @Column(name = "is_rejected", nullable = false)
//    private char isRejected = 'N'; // 'Y' = rejected, 'N' = not rejected
//    @Column(name = "approved_at")
//    private LocalDate approvedAt;
@Column(nullable = false)
private Boolean isDeleted = false;
//    @Column
//    private char deleted='N';
//
//
//    @Column
//    private String action;
//    @Column
//    private String rejectionMessage;

}
