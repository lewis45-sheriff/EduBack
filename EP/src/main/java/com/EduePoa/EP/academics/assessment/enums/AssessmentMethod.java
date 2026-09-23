package com.EduePoa.EP.academics.assessment.enums;

/**
 * How an assessment's evidence is captured/scored. Not every assessment yields a numeric
 * percentage — Pre-Primary observations and portfolios are captured differently from a written
 * end-term exam. The calculation engine branches on this method.
 */
public enum AssessmentMethod {
    /** Raw numeric score out of a maximum, normalized to a percentage. */
    PERCENTAGE,
    /** Rated against rubric criteria, each mapped to a performance level. */
    RUBRIC,
    /** Teacher observation resolved directly to a performance level. */
    OBSERVATION,
    /** Completed / not completed. */
    COMPLETION,
    /** Free-text qualitative comment only (no numeric level required). */
    QUALITATIVE
}
