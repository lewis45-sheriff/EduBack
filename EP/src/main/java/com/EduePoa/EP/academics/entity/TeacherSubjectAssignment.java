package com.EduePoa.EP.academics.entity;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.Stream.GradeStream;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

import java.time.Year;

/**
 * Assigns a teacher ({@link User}) to teach a learning area ({@link AcademicSubject}) for a
 * specific grade, optional stream, and academic year (optionally a term). This is what the
 * assessment layer checks to authorize a teacher to enter/approve marks for a class + learning area
 * (section 42). It complements {@link ClassSubjectAssignment}, which only says which subjects a
 * grade offers; this says <em>who teaches</em> them.
 * <p>
 * Uniqueness is scoped so the same teacher is not assigned twice to the identical
 * grade/stream/subject/year/term combination for a tenant. {@code grade_stream_id} and {@code term}
 * are nullable, so a stream/term-agnostic assignment is possible.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "teacher_subject_assignment",
        uniqueConstraints = @UniqueConstraint(columnNames = {
                "teacher_id", "grade_id", "grade_stream_id", "academic_subject_id", "year", "term", "tenant_id"}))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class TeacherSubjectAssignment extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    @JsonIgnore
    private User teacher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_subject_id", nullable = false)
    @JsonIgnore
    private AcademicSubject academicSubject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_id", nullable = false)
    @JsonIgnore
    private Grade grade;

    /** Optional stream scope; null means the assignment applies to the whole grade. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_stream_id")
    @JsonIgnore
    private GradeStream gradeStream;

    @Column(nullable = false)
    private Year year;

    /** Optional term scope; null means all terms in the year. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Term term;

    @Column(nullable = false)
    private Boolean active = true;
}
