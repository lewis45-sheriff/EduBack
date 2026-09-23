package com.EduePoa.EP.academics.curriculum.entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.academics.curriculum.enums.CurriculumStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

/**
 * An independently identifiable specific learning outcome (SLO) under a sub-strand. A teacher
 * can point an assessment at the exact outcome being assessed. Knowledge/skills/attitudes/values
 * dimensions are captured as free text where the design provides them.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cbc_learning_outcome",
        uniqueConstraints = @UniqueConstraint(columnNames = {"sub_strand_id", "code", "tenant_id"}))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class SpecificLearningOutcome extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_version_id", nullable = false)
    @JsonIgnore
    private CurriculumVersion curriculumVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sub_strand_id", nullable = false)
    @JsonIgnore
    private SubStrand subStrand;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(length = 1000)
    private String knowledge;

    @Column(length = 1000)
    private String skills;

    @Column(length = 1000)
    private String attitudes;

    /** "values" is a reserved SQL keyword, so the column is named value_dimension. */
    @Column(name = "value_dimension", length = 1000)
    private String values;

    @Column(nullable = false)
    private Integer sequence = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CurriculumStatus status = CurriculumStatus.ACTIVE;
}
