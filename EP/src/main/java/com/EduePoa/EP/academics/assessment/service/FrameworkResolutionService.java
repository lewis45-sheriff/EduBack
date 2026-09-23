package com.EduePoa.EP.academics.assessment.service;

import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.academics.assessment.entity.AssessmentFramework;

import java.util.Optional;

/**
 * Resolves which {@link AssessmentFramework} applies to a given grade under the active curriculum
 * version. The engine must know the framework <em>before</em> computing results so that, e.g., the
 * KJSEA framework (Grade 9) is used for Grade 9 but not for a Grade 2 internal assessment.
 */
public interface FrameworkResolutionService {

    /**
     * Best-match framework for a grade: prefers a framework whose gradeCode matches the grade's
     * mapping, then one matching the grade's education level, then any active framework of the
     * active curriculum version. Empty if none configured (caller falls back to legacy behaviour).
     */
    Optional<AssessmentFramework> resolveForGrade(Grade grade);
}
