package com.EduePoa.EP.PaymentTransfer.Request;

import lombok.*;

import java.math.BigDecimal;

/**
 * Maker request to initiate a payment transfer. The source student, invoice,
 * term, year and payment method are all derived from the payment identified by
 * {@code sourcePaymentReference}; only the destination and the amount to move
 * are supplied here.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreatePaymentTransferRequest {
    private String sourcePaymentReference;
    private BigDecimal amount;
    private Long destStudentId;
    private Long destInvoiceId;
    private String reason;
}
