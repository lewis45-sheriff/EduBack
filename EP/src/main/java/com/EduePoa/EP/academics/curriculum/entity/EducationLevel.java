package com.EduePoa.EP.academics.curriculum.entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.academics.curriculum.enums.CbcEducationLevel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

/**
 * A curriculum-version-scoped education level (Pre-Primary, Lower/Upper Primary, Junior/Senior
 * School). Backed by the {@link CbcEducationLevel} conceptual band but stored as data so a
 * version can describe its own set of levels and ordering.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cbc_education_level",
        uniqueConstraints = @UniqueConstraint(columnNames = {"curriculum_version_id", "code", "tenant_id"}))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class EducationLevel extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_version_id", nullable = false)
    @JsonIgnore
    private CurriculumVersion curriculumVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CbcEducationLevel band;

    /** Stable key within the version, e.g. UPPER_PRIMARY. */
    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false)
    private String name;

    /** Ordering of levels within the version (PP1 first ... Senior last). */
    @Column(nullable = false)
    private Integer sequence = 0;
}
