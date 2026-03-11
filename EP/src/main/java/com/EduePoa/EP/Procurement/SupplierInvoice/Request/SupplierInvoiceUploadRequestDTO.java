package com.EduePoa.EP.Procurement.SupplierInvoice.Request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
public class SupplierInvoiceUploadRequestDTO {

    @NotNull(message = "Purchase Order ID is required")
    private Long purchaseOrderId;

    @NotEmpty(message = "At least one Delivery Note ID is required")
    private List<Long> deliveryNoteIds;

    @NotNull(message = "Supplier ID is required")
    private Long supplierId;

    @NotBlank(message = "Invoice number is required")
    private String invoiceNumber;

    @NotNull(message = "Invoice date is required")
    private LocalDate invoiceDate;

    @NotBlank(message = "Supplier TIN is required for ETIMS compliance")
    @Pattern(regexp = "^[A-Z0-9]{11}$", message = "Invalid TIN format. Must be 11 alphanumeric characters")
    private String supplierTIN;

    @NotNull(message = "Total amount is required")
    @DecimalMin(value = "0.01", message = "Total amount must be greater than 0")
    private BigDecimal totalAmount;

    @NotNull(message = "VAT amount is required")
    @DecimalMin(value = "0.00", message = "VAT amount cannot be negative")
    private BigDecimal vatAmount;

    @NotEmpty(message = "At least one invoice item is required")
    @Valid
    private List<SupplierInvoiceItemRequestDTO> items;

    private String invoiceDocument; // Base64 encoded PDF/XML
}
