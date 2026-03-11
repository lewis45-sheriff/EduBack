package com.EduePoa.EP.academics.entity;


import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.StudentRegistration.Student;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.Year;


@Entity
@Data
@Table(name = "cbc_grade_result", uniqueConstraints = @UniqueConstraint(columnNames = { "student_id",
        "academic_subject_id", "term", "year" }))
public class CbcGradeResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "academic_subject_id", nullable = false)
    private AcademicSubject academicSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "term", nullable = false)
    private Term term;

    @Column(nullable = false)
    private Year year;

    private Double averageScore;

    private Integer cbcLevel;
    @Column(length = 30)
    private String cbcLabel;
}
