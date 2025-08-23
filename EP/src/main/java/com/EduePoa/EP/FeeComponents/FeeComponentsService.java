package com.EduePoa.EP.FeeComponents;

import com.EduePoa.EP.FeeComponents.Requests.FeeComponentRequest;
import com.EduePoa.EP.Utils.CustomResponse;

public interface FeeComponentsService {
    CustomResponse<?>create(FeeComponentRequest feeComponentRequest);
    CustomResponse<?>getAll();
    CustomResponse<?>delete(Long id);
    CustomResponse<?>getAllForFeeStructure();
}
