package com.EduePoa.EP.academics.assessment.entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * A weighted component within an {@link AssessmentFramework} (e.g. CAT 30%, End-Term 70%). Weights
 * belong to the framework, never globally hard-coded, so CAT/End-Term weighting is not blindly
 * applied to every grade. Normalized component scores are combined using {@code weight}.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cbc_assessment_component",
        uniqueConstraints = @UniqueConstraint(columnNames = {"assessment_framework_id", "code", "tenant_id"}))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class AssessmentComponent extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_framework_id", nullable = false)
    @JsonIgnore
    private AssessmentFramework assessmentFramework;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false)
    private String name;

    /** Weight as a fraction (0–1) or percentage (0–100); interpreted consistently per framework. */
    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal weight;

    /** Optional link to a legacy ExamType id so existing CAT/End-Term rows map to a component. */
    @Column(name = "exam_type_id")
    private Long examTypeId;

    @Column(nullable = false)
    private Integer sequence = 0;

    @Column(nullable = false)
    private Boolean active = true;
}
