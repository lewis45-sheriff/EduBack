package com.EduePoa.EP.academics.assessment.entity;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.academics.assessment.enums.RankingScope;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

import java.time.Year;

/**
 * Optional, school-configurable ranking policy. When {@code enabled} is false (the default), no
 * position is calculated or displayed. This deliberately keeps ranking OUT of the core CBC model so
 * the system never misrepresents ranking as a national CBC/KNEC requirement. A policy may be scoped
 * to a grade/stream/term/year, or left broad (nulls) as a tenant-wide default.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cbc_ranking_policy")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class RankingPolicy extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Boolean enabled = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RankingScope scope = RankingScope.NONE;

    @Column(name = "grade_id")
    private Long gradeId;

    @Column(name = "grade_stream_id")
    private Long gradeStreamId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Term term;

    private Year year;
}
