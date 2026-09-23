package com.EduePoa.EP.academics.assessment.entity;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.Stream.GradeStream;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.academics.assessment.enums.AssessmentMethod;
import com.EduePoa.EP.academics.assessment.enums.ResultStatus;
import com.EduePoa.EP.academics.curriculum.entity.CurriculumVersion;
import com.EduePoa.EP.academics.curriculum.entity.LearningArea;
import com.EduePoa.EP.academics.curriculum.entity.SpecificLearningOutcome;
import com.EduePoa.EP.academics.curriculum.entity.Strand;
import com.EduePoa.EP.academics.curriculum.entity.SubStrand;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;

/**
 * A planned/administered assessment. It ties an assessment event to the curriculum (learning area
 * and optionally the exact strand / sub-strand / learning outcome being assessed), to an
 * {@link AssessmentType} and {@link AssessmentFramework} (which supplies scoring rules), and to a
 * class context (grade/stream/term/year/teacher). Learner scores reference this assessment.
 * <p>
 * Not every assessment is numeric — {@code method} may be OBSERVATION/RUBRIC/COMPLETION/QUALITATIVE,
 * in which case {@code maximumScore} may be null.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cbc_assessment")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class Assessment extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_version_id")
    @JsonIgnore
    private CurriculumVersion curriculumVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_framework_id")
    @JsonIgnore
    private AssessmentFramework assessmentFramework;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_type_id")
    @JsonIgnore
    private AssessmentType assessmentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_component_id")
    @JsonIgnore
    private AssessmentComponent assessmentComponent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_area_id")
    @JsonIgnore
    private LearningArea learningArea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strand_id")
    @JsonIgnore
    private Strand strand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_strand_id")
    @JsonIgnore
    private SubStrand subStrand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_outcome_id")
    @JsonIgnore
    private SpecificLearningOutcome learningOutcome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    @JsonIgnore
    private Grade grade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_stream_id")
    @JsonIgnore
    private GradeStream gradeStream;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Term term;

    private Year year;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssessmentMethod method = AssessmentMethod.PERCENTAGE;

    /** Maximum raw score for numeric assessments; null for non-numeric methods. */
    @Column(precision = 8, scale = 2)
    private BigDecimal maximumScore;

    private LocalDate assessmentDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    @JsonIgnore
    private User teacher;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultStatus status = ResultStatus.DRAFT;
}
