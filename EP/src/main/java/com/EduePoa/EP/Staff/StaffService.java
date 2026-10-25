package com.EduePoa.EP.Staff;

import com.EduePoa.EP.Staff.Request.CreateStaffRequestDTO;
import com.EduePoa.EP.Staff.Request.PortalAccessRequestDTO;
import com.EduePoa.EP.Staff.Request.UpdateStaffRequestDTO;
import com.EduePoa.EP.Utils.CustomResponse;

public interface StaffService {
    CustomResponse<?> createStaff(CreateStaffRequestDTO request);
    CustomResponse<?> getAllStaff();

    /**
     * Returns only staff who are teachers — resolved from the linked system user's
     * {@code ROLE_TEACHER} role in the database. Backs teacher pickers (e.g. assign class teacher).
     */
    CustomResponse<?> getTeachers();

    /**
     * Ensures a teacher {@code User} has a matching {@link com.EduePoa.EP.Staff.Staff} record and
     * that the staff is linked back to the user. Idempotent: links an existing staff row (by
     * user id or email) if present, otherwise creates a TEACHER staff seeded from the user's
     * details. Called when a user is created with the teacher role so staff and user stay in sync.
     *
     * @return the resulting staff id, or null if it could not be created/linked.
     */
    Long ensureStaffForTeacherUser(com.EduePoa.EP.Authentication.User.User user);
    CustomResponse<?> getStaffById(Long id);
    CustomResponse<?> updateStaff(Long id, UpdateStaffRequestDTO request);
    CustomResponse<?> deleteStaff(Long id);
    CustomResponse<?> updatePortalAccess(Long id, PortalAccessRequestDTO request);

    /**
     * Returns the authenticated caller's own staff identity and class assignment.
     * Authorized by the caller's identity (JWT), not by staff:read.
     */
    CustomResponse<?> getMyAssignment();

    /**
     * Idempotently brings the current tenant's {@code ROLE_TEACHER} permission set up to date with
     * the Teacher Portal features. Safe to call repeatedly; only adds missing permissions.
     */
    CustomResponse<?> syncTeacherRolePermissions();
}
