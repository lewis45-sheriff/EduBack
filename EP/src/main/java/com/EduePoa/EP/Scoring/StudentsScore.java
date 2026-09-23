package com.EduePoa.EP.Scoring;


import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.academics.assessment.entity.Assessment;
import com.EduePoa.EP.academics.assessment.enums.ResultStatus;
import com.EduePoa.EP.academics.curriculum.entity.CurriculumVersion;
import com.EduePoa.EP.academics.entity.AcademicSubject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Year;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class StudentsScore extends TenantScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JsonIgnore
    private Student student;

    @ManyToOne
    @JsonIgnore
    private Grade grade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Term term;



    @ManyToOne
    @JsonIgnore
    private AcademicSubject academicSubject;

    @ManyToOne
    @JsonIgnore
    private User subjectTeacher;

    private char resultApproved = 'N';

    @ManyToOne
    @JsonIgnore
    private User resultApprovedBy;

    /**
     * Legacy raw mark. Retained for backward compatibility with existing modules
     * (ExamService, UploadMarks). New CBC code prefers {@link #rawScore} + {@link #maximumScore}.
     */
    private Double examScore;

    @ManyToOne
    @JsonIgnore
    private ExamType examType;

    private Year year;

    // --- CBC additive fields (all nullable; do not break existing rows) ---

    /** New raw score using BigDecimal precision. Falls back to {@link #examScore} when null. */
    @Column(precision = 8, scale = 2)
    private BigDecimal rawScore;

    /** Maximum attainable raw score for normalization. May come from the linked Assessment/ExamType. */
    @Column(precision = 8, scale = 2)
    private BigDecimal maximumScore;

    /** The curriculum version this score was captured under (preserves historical integrity). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private CurriculumVersion curriculumVersion;

    /** Optional link to a structured CBC Assessment (learning area / strand / outcome context). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private Assessment assessment;

    /** Richer approval lifecycle; coexists with the legacy resultApproved char flag. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ResultStatus resultStatus;

    /**
     * Returns the effective raw score, preferring the new BigDecimal field and falling back to the
     * legacy examScore. Null only when neither is set.
     */
    @Transient
    public BigDecimal effectiveRawScore() {
        if (rawScore != null) {
            return rawScore;
        }
        return examScore != null ? BigDecimal.valueOf(examScore) : null;
    }
}
