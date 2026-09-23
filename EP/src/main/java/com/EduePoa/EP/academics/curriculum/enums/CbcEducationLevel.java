package com.EduePoa.EP.academics.curriculum.enums;

/**
 * Broad Kenyan CBC/CBE education levels (KICD Basic Education Curriculum Framework).
 * <p>
 * These are the stable conceptual bands. The concrete grade &rarr; level mapping is kept
 * data-driven via {@code GradeLevelMapping} so future curriculum revisions do not require
 * Java changes. This enum only fixes the conceptual vocabulary, not per-grade behaviour.
 */
public enum CbcEducationLevel {
    PRE_PRIMARY("Pre-Primary Education"),
    LOWER_PRIMARY("Lower Primary"),
    UPPER_PRIMARY("Upper Primary"),
    JUNIOR_SCHOOL("Junior School"),
    SENIOR_SCHOOL("Senior School");

    private final String displayName;

    CbcEducationLevel(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
