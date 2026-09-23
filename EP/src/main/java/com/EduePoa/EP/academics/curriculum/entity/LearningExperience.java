package com.EduePoa.EP.academics.curriculum.entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.academics.curriculum.enums.CurriculumStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

/**
 * A suggested learning experience / activity. CBC is not only about marks, so activities are
 * first-class. A learning experience is anchored to a learning area and (optionally) a strand,
 * sub-strand and specific learning outcome, so a teacher can associate an assessment with the
 * experience or outcome being addressed.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cbc_learning_experience")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class LearningExperience extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_version_id", nullable = false)
    @JsonIgnore
    private CurriculumVersion curriculumVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_area_id")
    @JsonIgnore
    private LearningArea learningArea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strand_id")
    @JsonIgnore
    private Strand strand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_strand_id")
    @JsonIgnore
    private SubStrand subStrand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_outcome_id")
    @JsonIgnore
    private SpecificLearningOutcome learningOutcome;

    @Column(nullable = false)
    private String title;

    @Column(length = 3000)
    private String description;

    private String suggestedDuration;

    @Column(length = 2000)
    private String resources;

    @Column(length = 2000)
    private String inquiryQuestions;

    @Column(nullable = false)
    private Integer sequence = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CurriculumStatus status = CurriculumStatus.ACTIVE;
}
