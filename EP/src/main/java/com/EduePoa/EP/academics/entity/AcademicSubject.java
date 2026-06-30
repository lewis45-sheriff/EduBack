package com.EduePoa.EP.academics.entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "academic_subject")
public class AcademicSubject extends TenantScopedEntity {

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
