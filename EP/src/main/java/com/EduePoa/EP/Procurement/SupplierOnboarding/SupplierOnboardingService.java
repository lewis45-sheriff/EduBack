package com.EduePoa.EP.Procurement.SupplierOnboarding;


import com.EduePoa.EP.Procurement.SupplierOnboarding.Requests.SupplierOnboardingRequestDTO;
import com.EduePoa.EP.Procurement.SupplierOnboarding.Responses.SupplierOnboardingResponseDTO;
import com.EduePoa.EP.Utils.CustomResponse;

import java.util.List;

public interface SupplierOnboardingService {
    CustomResponse<?> registerSupplier(SupplierOnboardingRequestDTO supplierOnboardingRequestDTO);

    CustomResponse<SupplierOnboardingResponseDTO> getSupplierById(Long id);

    CustomResponse<List<SupplierOnboardingResponseDTO>> getAllSuppliers();

    CustomResponse<?> updateSupplier(Long id, SupplierOnboardingRequestDTO supplierOnboardingRequestDTO);

    CustomResponse<?> deleteSupplier(Long id);

    CustomResponse<?> approveSupplier(Long id);

    CustomResponse<?> rejectSupplier(Long id, String rejectionReason);

}
