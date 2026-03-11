package com.EduePoa.EP.Procurement.SupplierOnboarding.Requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SupplierRejectionRequestDTO {

    @NotBlank(message = "Rejection reason is required")
    private String rejectionReason;
}
