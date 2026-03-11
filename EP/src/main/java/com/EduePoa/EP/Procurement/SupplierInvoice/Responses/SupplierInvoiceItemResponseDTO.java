package com.EduePoa.EP.Procurement.SupplierInvoice.Responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierInvoiceItemResponseDTO {

    private Long id;
    private Long supplierInvoiceId;
    private Long purchaseOrderItemId;
    private String purchaseOrderItemDescription; // adjust field name to match your PurchaseOrderItem entity
    private Integer invoicedQuantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
}