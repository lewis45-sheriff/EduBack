package com.EduePoa.EP.academics.assessment.entity;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.Stream.GradeStream;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.StudentRegistration.Student;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Year;

/**
 * Per-learner, per-term academic summary. Deliberately keeps {@code overallPercentage} optional:
 * activity-based/developmental stages (e.g. Pre-Primary) and frameworks that do not aggregate
 * across learning areas (e.g. KJSEA) may leave it null and rely on the qualitative comment and
 * per-learning-area results instead. {@code position} is populated only when a ranking policy is
 * enabled.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cbc_student_term_summary",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "term", "year", "tenant_id"}))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class StudentTermSummary extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnore
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    @JsonIgnore
    private Grade grade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_stream_id")
    @JsonIgnore
    private GradeStream gradeStream;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Term term;

    @Column(nullable = false)
    private Year year;

    private Integer learningAreasAssessed;

    /** Optional aggregate; null for developmental/non-aggregating frameworks. */
    @Column(precision = 6, scale = 2)
    private BigDecimal overallPercentage;

    @Column(length = 30)
    private String overallPerformanceLabel;

    /** Only set when a ranking policy is enabled for the scope. */
    private Integer position;

    @Column(length = 2000)
    private String teacherOverallComment;

    @Column(length = 2000)
    private String headteacherComment;

    // Attendance snapshot (aggregated from Attendance; daily records are not duplicated here).
    private Integer daysPresent;
    private Integer daysAbsent;
    private Integer daysLate;
    private Integer daysExcused;
}
