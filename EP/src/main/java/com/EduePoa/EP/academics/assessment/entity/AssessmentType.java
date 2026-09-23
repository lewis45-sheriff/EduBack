package com.EduePoa.EP.academics.assessment.entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.academics.assessment.enums.AssessmentMethod;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

/**
 * A configurable assessment type: Formative, School-Based, Summative, Project, Practical,
 * Observation, Portfolio, Performance Task, Oral, Written, Activity, etc. Each type declares its
 * default {@link AssessmentMethod} so an activity-based type need not force a numeric score.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cbc_assessment_type",
        uniqueConstraints = @UniqueConstraint(columnNames = {"code", "tenant_id"}))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class AssessmentType extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssessmentMethod defaultMethod = AssessmentMethod.PERCENTAGE;

    /** Optional framework this type is scoped to; null means shared across frameworks. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_framework_id")
    @JsonIgnore
    private AssessmentFramework assessmentFramework;

    @Column(nullable = false)
    private Boolean active = true;
}
