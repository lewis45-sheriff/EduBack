package com.EduePoa.EP.Procurement.Inventory.Responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockRequisitionItemResponseDTO {
    private Long id;
    private Long inventoryItemId;
    private String itemName;
    private String unitOfMeasure;
    private Integer requestedQuantity;
    private Integer availableQuantity;
    private String remarks;
}
