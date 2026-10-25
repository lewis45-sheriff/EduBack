package com.EduePoa.EP.academics.curriculum.service;

import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.curriculum.dto.CurriculumDtos.*;

import java.util.List;

/**
 * Read-oriented curriculum API backing the curriculum management UI. All methods operate within the
 * active {@code CurriculumVersion} for the current tenant unless a versionId/gradeId scopes them.
 */
public interface CurriculumService {

    CustomResponse<List<CurriculumVersionDto>> getVersions();

    CustomResponse<List<GradeDto>> getGrades();

    /** Flat list of all active learning areas for the active curriculum version (subject picker). */
    CustomResponse<List<LearningAreaDto>> getAllLearningAreas();

    CustomResponse<List<LearningAreaDto>> getLearningAreasForGrade(Long gradeMappingId);

    CustomResponse<LearningAreaDto> getLearningArea(Long learningAreaId);

    CustomResponse<List<StrandDto>> getStrands(Long learningAreaId);

    CustomResponse<List<SubStrandDto>> getSubStrands(Long strandId);

    CustomResponse<List<LearningOutcomeDto>> getLearningOutcomes(Long subStrandId);

    CustomResponse<List<CompetencyDto>> getCompetencies();

    CustomResponse<List<ValueDto>> getValues();

    CustomResponse<List<PciDto>> getPcis();

    /**
     * Bridges the active curriculum version's grade mappings to the tenant's operational grades by
     * matching grade name (case-insensitive). Seeded {@code GradeLevelMapping} rows start with no
     * operational grade link, which breaks grade-scoped subject lookups in the UI; this fills that
     * link in. Idempotent — already-linked mappings are left as-is. Returns the updated grade list.
     */
    CustomResponse<List<GradeDto>> bridgeGradesToOperational();
}
