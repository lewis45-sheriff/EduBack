package com.EduePoa.EP.OrgUnit.Service;

import com.EduePoa.EP.OrgUnit.DTO.OrgUnitCreateRequest;
import com.EduePoa.EP.OrgUnit.DTO.OrgUnitReparentRequest;
import com.EduePoa.EP.OrgUnit.DTO.OrgUnitUpdateRequest;
import com.EduePoa.EP.Utils.CustomResponse;

public interface OrgUnitService {

    CustomResponse<?> create(OrgUnitCreateRequest request);

    CustomResponse<?> update(Long id, OrgUnitUpdateRequest request);

    CustomResponse<?> reparent(Long id, OrgUnitReparentRequest request);

    CustomResponse<?> deactivate(Long id);

    CustomResponse<?> getById(Long id);

    /** Flat list of all units for the tenant. */
    CustomResponse<?> getAll(boolean activeOnly);

    /** Full hierarchy as a nested tree rooted at the active root. */
    CustomResponse<?> getTree();
}
