package com.EduePoa.EP.Procurement.PurchaseOrderGeneration.Requests;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PurchaseOrderItemRequestDTO {
    private String itemName;
    private String description;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String unitOfMeasure;
}