package com.EduePoa.EP.academics.curriculum.enums;

/**
 * Distinguishes official KICD curriculum definitions from school-authored custom ones.
 * The default school experience uses OFFICIAL_KICD; SCHOOL_CUSTOM requires explicit
 * administrative action and must remain identifiable.
 */
public enum CurriculumSource {
    OFFICIAL_KICD,
    SCHOOL_CUSTOM
}
