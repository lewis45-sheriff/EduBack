package com.EduePoa.EP.Staff.Enum;

/**
 * Category of a staff member. Teachers ({@link #TEACHER}) are always provisioned
 * a system {@code User} account (they can log in). Other staff types are only
 * provisioned a login when portal access is explicitly enabled.
 */
public enum StaffType {
    TEACHER,
    HEAD_TEACHER,
    DEPUTY_HEAD_TEACHER,
    ADMINISTRATOR,
    ACCOUNTANT,
    LIBRARIAN,
    SECRETARY,
    SUPPORT,
    OTHER
}
