package com.EduePoa.EP.OrgUnit.Entity;

/**
 * Suggested default organizational-unit type labels. {@link OrgUnit#getType()} is stored as a
 * free-form string, NOT this enum, so administrators can introduce new types (e.g. "FACULTY",
 * "CAMPUS") without any code change. These constants exist only to seed sensible defaults and to
 * give callers stable names for the common cases (design §5.1).
 */
public final class OrgUnitType {

    private OrgUnitType() {
    }

    public static final String SCHOOL = "SCHOOL";
    public static final String DIVISION = "DIVISION";
    public static final String DEPARTMENT = "DEPARTMENT";
    public static final String SECTION = "SECTION";
    public static final String UNIT = "UNIT";
}
