package com.EduePoa.EP.Parents;

import com.EduePoa.EP.Parents.Request.CreateParentRequestDTO;
import com.EduePoa.EP.Parents.Request.PortalAccessRequestDTO;
import com.EduePoa.EP.Parents.Request.UpdateParentRequestDTO;
import com.EduePoa.EP.Utils.CustomResponse;

public interface ParentService {
    CustomResponse<?> getAllParents();
    CustomResponse<?> getParentById(Long id);
    CustomResponse<?> createParent(CreateParentRequestDTO request);
    CustomResponse<?> updateParent(Long id, UpdateParentRequestDTO request);
    CustomResponse<?> deleteParent(Long id);
    CustomResponse<?> updatePortalAccess(Long id, PortalAccessRequestDTO request);
}
