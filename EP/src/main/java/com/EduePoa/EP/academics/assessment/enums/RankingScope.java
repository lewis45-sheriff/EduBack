package com.EduePoa.EP.academics.assessment.enums;

/**
 * Scope over which optional school ranking is computed. Ranking is NOT an inherent CBC/KNEC
 * requirement — it is a school-configurable feature. NONE disables ranking entirely.
 */
public enum RankingScope {
    NONE,
    WITHIN_STREAM,
    WITHIN_GRADE,
    BOTH
}
