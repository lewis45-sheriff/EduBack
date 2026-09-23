package com.EduePoa.EP.academics.curriculum.entity;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.academics.curriculum.enums.CurriculumSource;
import com.EduePoa.EP.academics.curriculum.enums.CurriculumStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;

/**
 * A named, dated version of a curriculum (e.g. "CBC_2024"). Every downstream curriculum
 * definition (education levels, learning areas, strands, outcomes, competencies, values,
 * PCIs, assessment frameworks) is scoped to a curriculum version so that introducing a new
 * version never silently mutates historical learner records — those stay linked to the
 * version under which they were created.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cbc_curriculum_version",
        uniqueConstraints = @UniqueConstraint(columnNames = {"code", "tenant_id"}))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class CurriculumVersion extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable machine key, e.g. CBC_2024. Unique per tenant. */
    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false)
    private String name;

    /** Human/marketing version label, e.g. "2024". */
    @Column(length = 30)
    private String version;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CurriculumStatus status = CurriculumStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CurriculumSource source = CurriculumSource.OFFICIAL_KICD;

    @Column(length = 1000)
    private String description;
}
