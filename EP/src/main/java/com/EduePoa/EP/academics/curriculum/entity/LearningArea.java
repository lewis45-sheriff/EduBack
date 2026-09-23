package com.EduePoa.EP.academics.curriculum.entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.academics.curriculum.enums.CurriculumSource;
import com.EduePoa.EP.academics.curriculum.enums.CurriculumStatus;
import com.EduePoa.EP.academics.curriculum.enums.LearningAreaType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

/**
 * A curriculum learning area (e.g. Mathematics, Integrated Science, Language Activities) drawn
 * from the official KICD catalogue for a specific curriculum version and education level. This
 * is the catalogue authority — schools select/configure from these rather than inventing them
 * (see SchoolCurriculumConfiguration in a later phase). Custom areas are allowed only when
 * {@code source = SCHOOL_CUSTOM}.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cbc_learning_area",
        uniqueConstraints = @UniqueConstraint(columnNames = {"curriculum_version_id", "catalogue_key", "tenant_id"}))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class LearningArea extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_version_id", nullable = false)
    @JsonIgnore
    private CurriculumVersion curriculumVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "education_level_id")
    @JsonIgnore
    private EducationLevel educationLevel;

    /** Stable catalogue identity, e.g. MATHEMATICS, INTEGRATED_SCIENCE. Unique per version. */
    @Column(name = "catalogue_key", nullable = false, length = 80)
    private String catalogueKey;

    @Column(nullable = false)
    private String officialName;

    private String displayName;

    @Column(length = 30)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LearningAreaType learningAreaType = LearningAreaType.CORE;

    @Column(nullable = false)
    private Boolean isCore = false;

    @Column(nullable = false)
    private Boolean isOptional = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CurriculumSource source = CurriculumSource.OFFICIAL_KICD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CurriculumStatus status = CurriculumStatus.ACTIVE;

    @Column(nullable = false)
    private Integer sequence = 0;

    /**
     * Optional bridge to the legacy {@link com.EduePoa.EP.academics.entity.AcademicSubject}
     * so existing scoring rows can be reconciled with the catalogue during migration. Stored as
     * an id (not a hard FK) to avoid coupling the catalogue to the legacy table.
     */
    @Column(name = "academic_subject_id")
    private Long academicSubjectId;
}
