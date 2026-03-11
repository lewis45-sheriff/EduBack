package com.EduePoa.EP.Procurement.PurchaseOrderGeneration.Responses;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PurchaseOrderItemResponseDTO {
    private Long id;
    private String itemName;
    private String description;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private String unitOfMeasure;
}