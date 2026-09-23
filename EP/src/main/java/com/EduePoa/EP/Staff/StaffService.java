package com.EduePoa.EP.Staff;

import com.EduePoa.EP.Staff.Request.CreateStaffRequestDTO;
import com.EduePoa.EP.Staff.Request.PortalAccessRequestDTO;
import com.EduePoa.EP.Staff.Request.UpdateStaffRequestDTO;
import com.EduePoa.EP.Utils.CustomResponse;

public interface StaffService {
    CustomResponse<?> createStaff(CreateStaffRequestDTO request);
    CustomResponse<?> getAllStaff();
    CustomResponse<?> getStaffById(Long id);
    CustomResponse<?> updateStaff(Long id, UpdateStaffRequestDTO request);
    CustomResponse<?> deleteStaff(Long id);
    CustomResponse<?> updatePortalAccess(Long id, PortalAccessRequestDTO request);

    /**
     * Returns the authenticated caller's own staff identity and class assignment.
     * Authorized by the caller's identity (JWT), not by staff:read.
     */
    CustomResponse<?> getMyAssignment();
}
