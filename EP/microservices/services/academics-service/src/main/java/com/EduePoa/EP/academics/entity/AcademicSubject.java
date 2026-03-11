package com.EduePoa.EP.academics.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "academic_subject")
public class AcademicSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String subjectName;
    @Column(nullable = true)
    private String subjectCode;
    @Column(nullable = true)
    private String learningArea;
    @Column(nullable = false)
    private Boolean isCbcCore = false;

}
