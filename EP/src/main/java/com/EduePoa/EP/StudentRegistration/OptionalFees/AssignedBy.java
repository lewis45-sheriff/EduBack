package com.EduePoa.EP.StudentRegistration.OptionalFees;

/**
 * Identifies the type of actor that created an optional-fee assignment.
 * <p>
 * The value is always resolved server-side from the authenticated user's role
 * and is never trusted from client input.
 */
public enum AssignedBy {
    /** Assigned by authorized school staff (an administrator). */
    ADMIN,
    /** Self-assigned by an authorized parent for their own child. */
    PARENT
}
