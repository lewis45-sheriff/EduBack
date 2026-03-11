package com.EduePoa.EP.Procurement.Inventory.Responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransactionResponseDTO {
    private Long id;
    private Long inventoryItemId;
    private String itemName;
    private String transactionType;
    private Integer quantity;
    private Integer previousQuantity;
    private Integer newQuantity;
    private String referenceType;
    private Long referenceId;
    private String remarks;
    private String createdBy;
    private LocalDateTime createdAt;
}
