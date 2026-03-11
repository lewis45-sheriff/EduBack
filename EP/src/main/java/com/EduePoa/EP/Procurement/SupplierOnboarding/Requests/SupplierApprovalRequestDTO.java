package com.EduePoa.EP.Procurement.SupplierOnboarding.Requests;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierApprovalRequestDTO {

    @NotNull(message = "Supplier ID is required")
    private Long supplierId;

    @NotNull(message = "Approval decision is required")
    private Boolean approved;

    private String rejectionReason;
}
