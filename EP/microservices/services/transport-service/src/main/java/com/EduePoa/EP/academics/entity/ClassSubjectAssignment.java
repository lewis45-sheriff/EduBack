package com.EduePoa.EP.academics.entity;

import com.EduePoa.EP.Grade.Grade;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.Year;


@Entity
@Data
@Table(name = "class_subject_assignment", uniqueConstraints = @UniqueConstraint(columnNames = { "grade_id",
        "academic_subject_id", "year" }))
public class ClassSubjectAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "academic_subject_id", nullable = false)
    private AcademicSubject academicSubject;

    @Column(nullable = false)
    private Year year;
}
