package com.EduePoa.EP.Scoring;


import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.academics.entity.AcademicSubject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Year;

@Entity
@Data
public class StudentsScore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JsonIgnore
    private Student student;

    @ManyToOne
    @JsonIgnore
    private Grade grade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Term term;



    @ManyToOne
    @JsonIgnore
    private AcademicSubject academicSubject;

    @ManyToOne
    @JsonIgnore
    private User subjectTeacher;

    private char resultApproved = 'N';

    @ManyToOne
    @JsonIgnore
    private User resultApprovedBy;

    private Double examScore;

    @ManyToOne
    @JsonIgnore
    private ExamType examType;

    private Year year;
}
