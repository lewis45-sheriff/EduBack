package com.EduePoa.EP.Procurement.DeliveryNote.Responses;

import com.EduePoa.EP.Authentication.Enum.DeliveryNoteStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryNoteResponseDTO {

    private Long id;
    private Long purchaseOrderId;
    private String purchaseOrderNumber;
    private LocalDate deliveryDate;
    private String deliveredBy;
    private String receivedBy;
    private DeliveryNoteStatus status;
    private String rejectionReason;

    private List<DeliveryNoteItemResponseDTO> items;

    // Approval info
    private String createdByName;
    private LocalDateTime createdAt;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private String rejectedByName;
    private LocalDateTime rejectedAt;

    // Audit
    private String updatedByName;
    private LocalDateTime updatedAt;

    private boolean hasDeliveryDocument;
}