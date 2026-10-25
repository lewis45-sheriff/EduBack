package com.EduePoa.EP.Parents;

import com.EduePoa.EP.Parents.Request.CreateParentRequestDTO;
import com.EduePoa.EP.Parents.Request.PortalAccessRequestDTO;
import com.EduePoa.EP.Parents.Request.UpdateParentRequestDTO;
import com.EduePoa.EP.Utils.CustomResponse;

public interface ParentService {
    CustomResponse<?> getAllParents();
    CustomResponse<?> getParentById(Long id);
    CustomResponse<?> getParentStudents(Long parentId);
    CustomResponse<?> createParent(CreateParentRequestDTO request);
    CustomResponse<?> updateParent(Long id, UpdateParentRequestDTO request);
    CustomResponse<?> deleteParent(Long id);
    CustomResponse<?> updatePortalAccess(Long id, PortalAccessRequestDTO request);

    /**
     * The id of the {@link Parent} linked to the currently authenticated user, or {@code null} if
     * there is no authenticated user or no (non-deleted) parent profile is linked to it.
     */
    Long getCurrentParentId();

    /**
     * Whether the currently authenticated parent is a guardian of the given student (within the
     * current tenant). Returns {@code false} when there is no authenticated parent, the student is
     * not linked to them, or the link is in another tenant. Used to authorize parent-scoped reads
     * such as a child's report card without granting the admin-wide {@code report:generate} right.
     */
    boolean currentParentOwnsStudent(Long studentId);
}
