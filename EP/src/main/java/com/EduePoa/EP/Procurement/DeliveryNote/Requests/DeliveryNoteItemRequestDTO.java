package com.EduePoa.EP.Procurement.DeliveryNote.Requests;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryNoteItemRequestDTO {

    @NotNull(message = "Purchase Order Item ID is required")
    private Long purchaseOrderItemId;

    @NotNull(message = "Delivered quantity is required")
    @Min(value = 1, message = "Delivered quantity must be at least 1")
    private Integer deliveredQuantity;

    private String remarks;
}
