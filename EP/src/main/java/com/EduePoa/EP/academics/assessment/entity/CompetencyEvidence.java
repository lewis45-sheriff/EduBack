package com.EduePoa.EP.academics.assessment.entity;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.academics.curriculum.entity.CoreCompetency;
import com.EduePoa.EP.academics.curriculum.entity.LearningArea;
import com.EduePoa.EP.academics.curriculum.entity.SpecificLearningOutcome;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.Year;

/**
 * Evidence-based competency assessment for a learner. Competencies are NOT derived from subject
 * marks — a teacher records observed evidence and assigns a performance level, so the school can
 * always explain why a competency level was given. This is the authoritative pathway for
 * competency achievement (automated inference, if ever added, would be a separate rule engine).
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cbc_competency_evidence")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class CompetencyEvidence extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnore
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competency_id", nullable = false)
    @JsonIgnore
    private CoreCompetency competency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    @JsonIgnore
    private Grade grade;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Term term;

    private Year academicYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_area_id")
    @JsonIgnore
    private LearningArea learningArea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_outcome_id")
    @JsonIgnore
    private SpecificLearningOutcome learningOutcome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id")
    @JsonIgnore
    private Assessment assessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    @JsonIgnore
    private User teacher;

    /** The assigned performance level (configurable band), never a hard-coded number. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_level_id")
    @JsonIgnore
    private PerformanceLevel performanceLevel;

    @Column(length = 2000)
    private String evidenceDescription;

    @Column(length = 2000)
    private String comment;

    private LocalDate assessmentDate;
}
