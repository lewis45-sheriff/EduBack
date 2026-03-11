package com.EduePoa.EP.Procurement.DeliveryNote.Responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryNoteItemResponseDTO {

    private Long id;
    private Long deliveryNoteId;
    private Long purchaseOrderItemId;
    private String itemName;
    private String itemDescription;
    private Integer orderedQuantity;
    private Integer deliveredQuantity;
    private String remarks;
}
