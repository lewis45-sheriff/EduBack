package com.EduePoa.EP.academics.assessment.service;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.StudentRegistration.Student;

import java.time.Year;
import java.util.Optional;

/**
 * Optional, school-configurable ranking. If no enabled {@link com.EduePoa.EP.academics.assessment.entity.RankingPolicy}
 * applies, position resolution returns empty and nothing is displayed. Ranking is intentionally not
 * part of the core result computation.
 */
public interface RankingService {

    /** True if an enabled ranking policy applies to this student's grade/stream for the term/year. */
    boolean isRankingEnabled(Student student, Term term, Year year);

    /**
     * Resolve the student's position within the configured scope, or empty when ranking is disabled
     * or no aggregate is available. Ties share the same position (standard competition ranking).
     */
    Optional<Integer> resolvePosition(Student student, Term term, Year year);

    /** The scope label of the applicable enabled policy, or null when disabled. */
    String resolveScopeLabel(Student student, Term term, Year year);
}
