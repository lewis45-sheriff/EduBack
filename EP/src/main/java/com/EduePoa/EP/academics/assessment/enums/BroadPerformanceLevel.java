package com.EduePoa.EP.academics.assessment.enums;

/**
 * The four broad CBC performance categories. Finer sub-levels (e.g. KJSEA's EE1/EE2) are stored
 * as configurable {@code PerformanceLevel} rows that reference one of these broad bands. Points
 * and score ranges are never hard-coded here — they live on the framework's performance levels.
 */
public enum BroadPerformanceLevel {
    EXCEEDING_EXPECTATION("EE", 4, "Exceeding Expectation"),
    MEETING_EXPECTATION("ME", 3, "Meeting Expectation"),
    APPROACHING_EXPECTATION("AE", 2, "Approaching Expectation"),
    BELOW_EXPECTATION("BE", 1, "Below Expectation");

    private final String abbreviation;
    private final int broadLevel;
    private final String label;

    BroadPerformanceLevel(String abbreviation, int broadLevel, String label) {
        this.abbreviation = abbreviation;
        this.broadLevel = broadLevel;
        this.label = label;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public int getBroadLevel() {
        return broadLevel;
    }

    public String getLabel() {
        return label;
    }
}
