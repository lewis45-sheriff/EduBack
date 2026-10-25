package com.EduePoa.EP.OrgUnit.Entity;

/**
 * Lifecycle status of an organizational unit. Deactivation (INACTIVE) is preferred over
 * physical deletion once a unit has historical budget references, so historical reports
 * and allocations remain intact (design §5.2).
 */
public enum OrgUnitStatus {
    ACTIVE,
    INACTIVE
}
