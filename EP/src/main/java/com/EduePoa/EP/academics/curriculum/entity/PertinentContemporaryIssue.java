package com.EduePoa.EP.academics.curriculum.entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.academics.curriculum.enums.CurriculumStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

/**
 * A Pertinent and Contemporary Issue (PCI) — e.g. health, environmental, citizenship, safety,
 * financial literacy. PCIs are configurable catalogue data scoped to a curriculum version and are
 * never hard-coded into learning areas. A {@code category} groups related issues loosely.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cbc_pertinent_contemporary_issue",
        uniqueConstraints = @UniqueConstraint(columnNames = {"curriculum_version_id", "code", "tenant_id"}))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class PertinentContemporaryIssue extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_version_id", nullable = false)
    @JsonIgnore
    private CurriculumVersion curriculumVersion;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 80)
    private String category;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private Integer sequence = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CurriculumStatus status = CurriculumStatus.ACTIVE;
}
