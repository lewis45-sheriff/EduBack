package com.EduePoa.EP.Procurement.PurchaseOrderGeneration.Responses;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PurchaseOrderResponseDTO {
    private Long id;
    private String poNumber;
    private String supplierName;
    private LocalDate orderDate;
    private LocalDate expectedDeliveryDate;
    private String status;
    private BigDecimal totalAmount;
    private List<PurchaseOrderItemResponseDTO> items;
    private String approvedByName;
    private String approvedByEmail;
    private LocalDateTime approvedAt;

    private String rejectedByName;
    private String rejectedByEmail;
    private LocalDateTime rejectedAt;
    private String rejectionReason;
    private Integer rejectionCount;
}
