package com.EduePoa.EP.Authentication.Enum;

import lombok.Getter;

@Getter
public enum Permissions {
    // User & Role Management
    USER_CREATE("user:create", "Create new users (Staff, Admins)"),
    USER_READ("user:read", "View user details"),
    USER_UPDATE("user:update", "Edit user details"),
    USER_DELETE("user:delete", "Delete/Deactivate users"),
    ROLE_CREATE("role:create", "Create new roles"),
    ROLE_READ("role:read", "View roles and permissions"),
    ROLE_UPDATE("role:update", "Edit role permissions"),
    ROLE_DELETE("role:delete", "Delete roles"),

    // Student Management
    STUDENT_CREATE("student:create", "Enroll new students"),
    STUDENT_READ("student:read", "View student profiles"),
    STUDENT_UPDATE("student:update", "Update student information"),
    STUDENT_DELETE("student:delete", "Remove students"),
    STUDENT_PROMOTE("student:promote", "Promote students to the next grade"),

    // Academic Operations
    CLASS_READ("class:read", "View classes and streams"),
    CLASS_UPDATE("class:update", "Assign teachers to classes"),
    SUBJECT_CREATE("subject:create", "Add new subjects"),
    SUBJECT_READ("subject:read", "View subjects"),
    EXAM_CREATE("exam:create", "Schedule exams"),
    EXAM_READ("exam:read", "View exam schedules"),
    EXAM_GRADE("exam:grade", "Enter/Edit student marks"),
    REPORT_GENERATE("report:generate", "Generate academic report cards"),
    TIMETABLE_MANAGE("timetable:manage", "Create and edit timetables"),
    ATTENDANCE_MARK("attendance:mark", "Mark student attendance"),
    ATTENDANCE_READ("attendance:read", "View attendance records"),

    // Financial Management
    FEE_STRUCTURE_MANAGE("fee_structure:manage", "Create/Update fee structures"),
    FEE_COLLECT("fee:collect", "Record fee payments"),
    FEE_READ("fee:read", "View payment history and balances"),
    INVOICE_CREATE("invoice:create", "Generate invoices"),
    INVOICE_READ("invoice:read", "View invoices"),
    EXPENSE_CREATE("expense:create", "Record school expenses"),
    EXPENSE_READ("expense:read", "View expense reports"),
    FINANCIAL_REPORT_READ("financial_report:read", "Access financial summaries (revenue, balances)"),

    // Transport Management
    VEHICLE_MANAGE("vehicle:manage", "Add/Edit/Delete vehicles"),
    ROUTE_MANAGE("route:manage", "Add/Edit/Delete transport routes"),
    TRANSPORT_ASSIGN("transport:assign", "Assign students to transport"),
    TRANSPORT_READ("transport:read", "View transport details"),

    // Communication
    ANNOUNCEMENT_CREATE("announcement:create", "Post school-wide announcements"),
    MESSAGE_SEND("message:send", "Send SMS/Emails to parents/students"),
    COMMUNICATION_READ("communication:read", "View sent messages logs"),

    // Staff & HR
    STAFF_CREATE("staff:create", "Add new staff members"),
    STAFF_READ("staff:read", "View staff profiles"),
    STAFF_UPDATE("staff:update", "Update staff details"),
    PAYROLL_MANAGE("payroll:manage", "Manage staff salaries"),
    LEAVE_MANAGE("leave:manage", "Approve/Reject leave requests"),

    // System Configuration
    SETTINGS_MANAGE("settings:manage", "Update school info, branding, general settings"),
    AUDIT_READ("audit:read", "View system logs");

    private final String permission;
    private final String description;

    Permissions(String permission, String description) {
        this.permission = permission;
        this.description = description;
    }

    // Helper method to get permission by string value
    public static Permissions fromString(String permission) {
        for (Permissions p : Permissions.values()) {
            if (p.permission.equalsIgnoreCase(permission)) {
                return p;
            }
        }
        throw new IllegalArgumentException("No permission found with value: " + permission);
    }
}