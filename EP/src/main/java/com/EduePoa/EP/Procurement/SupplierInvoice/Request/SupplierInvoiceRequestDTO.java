package com.EduePoa.EP.Procurement.SupplierInvoice.Request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierInvoiceRequestDTO {

    private Long purchaseOrderId;

    private List<Long> deliveryNoteIds;

    private Long supplierId;

    private String invoiceNumber;

    private LocalDate invoiceDate;

    private BigDecimal vatAmount;

//    private InvoiceStatus status;

    private String invoiceDocument;

    private List<SupplierInvoiceItemRequestDTO> items;
}