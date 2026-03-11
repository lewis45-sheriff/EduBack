package com.EduePoa.EP.Procurement.Inventory.Requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockRequisitionItemRequestDTO {
    private Long inventoryItemId;
    private Integer requestedQuantity;
    private String remarks;
}
