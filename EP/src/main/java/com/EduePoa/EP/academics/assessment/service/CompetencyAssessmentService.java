package com.EduePoa.EP.academics.assessment.service;

import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.assessment.dto.RecordCompetencyEvidenceRequest;

public interface CompetencyAssessmentService {

    CustomResponse<?> recordEvidence(RecordCompetencyEvidenceRequest request);

    CustomResponse<?> getEvidenceForLearner(Long studentId, Long termId, int year);

    /**
     * List recorded competency evidence, filtered by any combination of grade, term, year and
     * learner. Used by the admin "Competency Evidence" screen which scopes by grade.
     */
    CustomResponse<?> listEvidence(Long gradeId, Long termId, Integer year, Long studentId);

    /**
     * List the performance levels (code, label, points) of the framework that applies to a learner,
     * so the record-evidence UI can offer the valid levels as a dropdown rather than hard-coding them.
     */
    CustomResponse<?> getPerformanceLevelsForLearner(Long studentId);
}
