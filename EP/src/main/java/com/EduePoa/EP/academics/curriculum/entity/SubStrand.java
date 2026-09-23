package com.EduePoa.EP.academics.curriculum.entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.academics.curriculum.enums.CurriculumStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

/**
 * A sub-strand within a strand. Stored as its own row so specific learning outcomes,
 * inquiry questions and learning experiences can attach to it precisely.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cbc_sub_strand",
        uniqueConstraints = @UniqueConstraint(columnNames = {"strand_id", "code", "tenant_id"}))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class SubStrand extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_version_id", nullable = false)
    @JsonIgnore
    private CurriculumVersion curriculumVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strand_id", nullable = false)
    @JsonIgnore
    private Strand strand;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    /** Suggested lesson allocation for the sub-strand, where the design specifies one. */
    private Integer suggestedLessons;

    @Column(nullable = false)
    private Integer sequence = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CurriculumStatus status = CurriculumStatus.ACTIVE;
}
