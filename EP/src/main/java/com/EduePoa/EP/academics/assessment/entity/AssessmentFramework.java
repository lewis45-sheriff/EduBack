package com.EduePoa.EP.academics.assessment.entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.academics.curriculum.entity.CurriculumVersion;
import com.EduePoa.EP.academics.curriculum.enums.CbcEducationLevel;
import com.EduePoa.EP.academics.curriculum.enums.CurriculumStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

/**
 * An assessment framework/configuration. It scopes performance levels, components and reporting
 * rules to a curriculum version and (optionally) an education level or grade so that, for example,
 * the KJSEA framework's rules are NOT applied to an internal Grade 2 activity assessment.
 * <p>
 * Whether an aggregate percentage across learning areas is produced is configurable via
 * {@code aggregateAcrossLearningAreas} — the KJSEA framework sets this to false because KNEC does
 * not combine the subjects into a single aggregate.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cbc_assessment_framework",
        uniqueConstraints = @UniqueConstraint(columnNames = {"curriculum_version_id", "code", "tenant_id"}))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class AssessmentFramework extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_version_id", nullable = false)
    @JsonIgnore
    private CurriculumVersion curriculumVersion;

    /** Stable key, e.g. KJSEA, INTERNAL_FORMATIVE, PRE_PRIMARY_OBSERVATION. */
    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    /** Optional education-level scope; null means the framework can apply across levels. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CbcEducationLevel educationLevel;

    /** Optional grade-code scope (e.g. GRADE_9); null means not grade-specific. */
    @Column(length = 20)
    private String gradeCode;

    /**
     * When false, results are reported per learning area / competency only and no single aggregate
     * percentage/points is computed across learning areas (the KJSEA rule).
     */
    @Column(nullable = false)
    private Boolean aggregateAcrossLearningAreas = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CurriculumStatus status = CurriculumStatus.ACTIVE;
}
