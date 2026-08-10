package com.EduePoa.EP.academics.entity;

import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;
import java.time.Year;


@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "class_subject_assignment", uniqueConstraints = @UniqueConstraint(columnNames = { "grade_id",
        "academic_subject_id", "year" }))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class ClassSubjectAssignment extends TenantScopedEntity {

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
