package com.EduePoa.EP.academics.assessment.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Request to record teacher-observed competency evidence for one learner in a term.
 *
 * <p>Competencies are evidence-based: the teacher records an observed performance level (by its
 * configured code, e.g. {@code ME}/{@code EE}) and optional comment per core competency. The level
 * is never derived from exam marks. One request may carry several competency observations.
 */
@Data
public class RecordCompetencyEvidenceRequest {

    /** The learner being assessed. */
    private Long studentId;

    /** 1 = TERM_1, 2 = TERM_2, 3 = TERM_3. */
    private Integer termId;

    private Integer year;

    /** One entry per core competency observed. */
    private List<CompetencyEntry> competencies = new ArrayList<>();

    @Data
    public static class CompetencyEntry {
        /** The core competency catalogue id (from the curriculum). */
        private Long competencyId;

        /**
         * Performance level code for the student's framework (e.g. {@code ME}, {@code EE}). Optional:
         * when omitted the observation is recorded with a comment only and no level.
         */
        private String performanceLevelCode;

        /** Optional free-text evidence/observation. */
        private String comment;

        /** Optional learning-area (curriculum catalogue) this observation relates to. */
        private Long learningAreaId;
    }
}
