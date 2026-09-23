package com.EduePoa.EP.academics.curriculum.entity;

import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

/**
 * Data-driven mapping between an existing {@link Grade} (with its integer gradeNumber) and a
 * CBC {@link EducationLevel} for a given curriculum version. This is the single place that
 * decides "Grade 7 belongs to Junior School" so that no Java code has to switch on gradeNumber
 * and future curriculum revisions can remap grades to levels without code changes.
 * <p>
 * {@code gradeCode} (e.g. "PP1", "GRADE_7") lets us represent Pre-Primary grades that may not
 * yet exist as {@link Grade} rows for a given school.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cbc_grade_level_mapping",
        uniqueConstraints = @UniqueConstraint(columnNames = {"curriculum_version_id", "grade_code", "tenant_id"}))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class GradeLevelMapping extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_version_id", nullable = false)
    @JsonIgnore
    private CurriculumVersion curriculumVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "education_level_id", nullable = false)
    @JsonIgnore
    private EducationLevel educationLevel;

    /** Stable curriculum grade key, e.g. PP1, PP2, GRADE_1 ... GRADE_12. */
    @Column(name = "grade_code", nullable = false, length = 20)
    private String gradeCode;

    @Column(nullable = false)
    private String gradeName;

    /** Optional link to the school's operational Grade row (may be null for PP1/PP2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    @JsonIgnore
    private Grade grade;

    @Column(nullable = false)
    private Integer sequence = 0;
}
