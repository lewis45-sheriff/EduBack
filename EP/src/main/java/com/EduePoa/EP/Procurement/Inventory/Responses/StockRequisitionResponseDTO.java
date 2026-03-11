package com.EduePoa.EP.Procurement.Inventory.Responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockRequisitionResponseDTO {
    private Long id;
    private String requisitionNumber;
    private String purpose;
    private String status;
    private List<StockRequisitionItemResponseDTO> items;
    private String createdBy;
    private LocalDateTime createdAt;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String rejectedBy;
    private LocalDateTime rejectedAt;
    private String rejectionReason;
}
