package com.EduePoa.EP.Procurement.DeliveryNote.Requests;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryNoteApprovalRequestDTO {

    @NotNull(message = "Delivery Note ID is required")
    private Long deliveryNoteId;

    @NotNull(message = "Approval decision is required")
    private Boolean approved;

    private String rejectionReason;
}
