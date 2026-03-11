package com.EduePoa.EP.Procurement.SupplierPayments.Responses;

import com.EduePoa.EP.Authentication.Enum.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponseDTO {

    private Long id;
    private Long invoiceId;
    private String invoiceNumber;
    private Long supplierId;
    private String supplierName;
    private LocalDate paymentDate;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private String referenceNumber;
    private String remarks;

    // Invoice balance context
    private BigDecimal invoiceTotalAmount;
    private BigDecimal totalPaidAmount;
    private BigDecimal outstandingBalance;
    private String invoiceStatus;
    private String paymentStatus;

    // Audit fields
    private String createdBy;
    private LocalDateTime createdAt;
    private String approvedBy;
    private LocalDateTime approvedAt;
}
