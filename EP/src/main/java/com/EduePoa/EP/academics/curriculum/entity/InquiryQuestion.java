package com.EduePoa.EP.academics.curriculum.entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

/**
 * A key inquiry question attached to a specific learning outcome. A learning outcome can have many
 * inquiry questions; each is modelled separately (never as a blob on the outcome).
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cbc_inquiry_question")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class InquiryQuestion extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_version_id", nullable = false)
    @JsonIgnore
    private CurriculumVersion curriculumVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learning_outcome_id", nullable = false)
    @JsonIgnore
    private SpecificLearningOutcome learningOutcome;

    @Column(nullable = false, length = 1000)
    private String question;

    @Column(nullable = false)
    private Integer sequence = 0;
}
