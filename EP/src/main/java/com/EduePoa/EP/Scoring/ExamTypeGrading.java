package com.EduePoa.EP.Scoring;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

/**
 * @deprecated Legacy A–E / points-and-remarks grading band. This model predates the CBC
 * assessment framework and is <strong>not</strong> used by the CBC calculator. Performance
 * levels are now configuration data on {@code com.EduePoa.EP.academics.assessment.PerformanceLevel}
 * scoped to an {@code AssessmentFramework}. This class is retained (not deleted) because
 * {@code ExamService} and its DTOs still reference it; it must be removed only after a full
 * dependency review. See docs/cbc-module-implementation-analysis.md (migration strategy).
 */
@Deprecated
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class ExamTypeGrading extends TenantScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "exam_type_id", referencedColumnName = "id", nullable =false)
    private ExamType examType;

    @Column(nullable = false)
    private Double start;

    @Column(nullable = false)
    private Double end;

    @Column(nullable = true)
    private Double point;

    @Column(nullable = false)
    private String remarks;
}
