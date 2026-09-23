package com.EduePoa.EP.academics.entity;


import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.academics.assessment.entity.PerformanceLevel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Year;


@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cbc_grade_result", uniqueConstraints = @UniqueConstraint(columnNames = { "student_id",
        "academic_subject_id", "term", "year" }))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class CbcGradeResult extends TenantScopedEntity {

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

    /** Legacy 1–4 broad level. Retained; new code prefers {@link #performanceLevel}. */
    private Integer cbcLevel;
    @Column(length = 30)
    private String cbcLabel;

    // --- CBC framework-driven additive fields (all nullable; preserve historical rows) ---

    /** Normalized percentage the result was computed from (0–100). */
    @Column(precision = 6, scale = 2)
    private BigDecimal normalizedScore;

    /** The configurable performance level assigned (e.g. KJSEA ME1). Null for legacy rows. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_level_id")
    @JsonIgnore
    private PerformanceLevel performanceLevel;

    /** Points for the level (e.g. KJSEA ME1 = 6), when the framework awards points. */
    private Integer performancePoints;

    @Column(length = 1000)
    private String teacherComment;

    // Reproducibility: record which framework/version produced this result.
    @Column(name = "assessment_framework_id")
    private Long assessmentFrameworkId;

    @Column(name = "curriculum_version_id")
    private Long curriculumVersionId;

    /** Free-form calculation version tag for auditing when rules change. */
    @Column(length = 30)
    private String calculationVersion;
}
