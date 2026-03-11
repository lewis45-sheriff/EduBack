package com.EduePoa.EP.Procurement.SupplierInvoice.Request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierInvoiceApprovalRequestDTO {

    @NotNull(message = "Invoice ID is required")
    private Long invoiceId;

    @NotNull(message = "Approval decision is required")
    private Boolean approved;

    private String rejectionReason;
}
