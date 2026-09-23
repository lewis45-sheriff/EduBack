package com.EduePoa.EP.Budget.Enum;

/**
 * Lifecycle status for a budget category. Deactivation (INACTIVE) is preferred over
 * physical deletion when a category has been used, to keep historical reports stable.
 */
public enum CategoryStatus {
    ACTIVE,
    INACTIVE
}
