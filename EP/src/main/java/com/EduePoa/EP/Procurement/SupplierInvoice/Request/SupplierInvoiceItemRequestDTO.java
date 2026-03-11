package com.EduePoa.EP.Procurement.SupplierInvoice.Request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierInvoiceItemRequestDTO {
    private Long purchaseOrderItemId;
    private Integer invoicedQuantity;
    private BigDecimal unitPrice;
}