package com.EduePoa.EP.Procurement.SupplierInvoice.Responses;

import com.EduePoa.EP.Authentication.Enum.InvoiceStatus;
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
public class SupplierInvoiceResponseDTO {

    private Long id;
    private Long purchaseOrderId;
    private String purchaseOrderReference;
    private List<Long> deliveryNoteIds;
    private Long supplierId;
    private String supplierName;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private BigDecimal totalAmount;
    private BigDecimal vatAmount;
    private InvoiceStatus status;
    private String invoiceDocument;
    private List<SupplierInvoiceItemResponseDTO> items;
}