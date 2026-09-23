package com.EduePoa.EP.academics.assessment.entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.academics.assessment.enums.BroadPerformanceLevel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * A configurable performance level/band belonging to an {@link AssessmentFramework}. This is the
 * single most important "no hard-coding" entity: score boundaries and points live here as data,
 * so KJSEA's EE1–BE2 (with their 90–100=8 ... 0–10=1 ranges) and any future KNEC revision are
 * expressed as rows, never as {@code if (score >= 80)} in Java.
 * <p>
 * {@code minScore}/{@code maxScore} are inclusive percentage bounds. A given framework must not
 * define overlapping bands (validated by the service layer).
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cbc_performance_level",
        uniqueConstraints = @UniqueConstraint(columnNames = {"assessment_framework_id", "code", "tenant_id"}))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class PerformanceLevel extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_framework_id", nullable = false)
    @JsonIgnore
    private AssessmentFramework assessmentFramework;

    /** Fine-grained level key, e.g. EE1, ME2, or a single-tier LEVEL_4. */
    @Column(nullable = false, length = 20)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BroadPerformanceLevel broadLevel;

    @Column(nullable = false)
    private String label;

    @Column(length = 20)
    private String abbreviation;

    /** Inclusive lower percentage bound (0–100). */
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal minScore;

    /** Inclusive upper percentage bound (0–100). */
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal maxScore;

    /** Points awarded for this level (e.g. KJSEA EE1 = 8). Optional for non-points frameworks. */
    private Integer points;

    @Column(length = 1000)
    private String descriptor;

    @Column(nullable = false)
    private Integer sequence = 0;

    /** True if a percentage falls within [minScore, maxScore] inclusive. */
    public boolean matches(BigDecimal percentage) {
        return percentage != null
                && percentage.compareTo(minScore) >= 0
                && percentage.compareTo(maxScore) <= 0;
    }
}
