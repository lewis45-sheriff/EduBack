package com.EduePoa.EP.FeeStructure;

import com.EduePoa.EP.FeeStructure.Requests.FeeStructureRequestDTO;
import com.EduePoa.EP.Utils.CustomResponse;

public interface FeeStructureService {
    CustomResponse<?>create(FeeStructureRequestDTO feeStructureRequestDTO);
    CustomResponse<?>getAllFeeStructures();
    CustomResponse<?>getFeeStructureById(Long id);
}
