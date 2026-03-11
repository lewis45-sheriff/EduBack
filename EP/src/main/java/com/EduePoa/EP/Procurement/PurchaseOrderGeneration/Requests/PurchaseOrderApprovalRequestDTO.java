package com.EduePoa.EP.Procurement.PurchaseOrderGeneration.Requests;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderApprovalRequestDTO {

    @NotNull(message = "Purchase Order ID is required")
    private Long purchaseOrderId;

    @NotNull(message = "Approval decision is required")
    private Boolean approved;

    private String rejectionReason;
}
