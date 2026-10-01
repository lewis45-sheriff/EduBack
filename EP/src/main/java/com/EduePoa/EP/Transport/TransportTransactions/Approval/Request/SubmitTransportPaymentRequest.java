package com.EduePoa.EP.Transport.TransportTransactions.Approval.Request;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Transport.TransportType;
import lombok.Data;

/**
 * Maker's request to record a transport fee payment. Mirrors
 * {@code TransportTransactionRequestDTO} plus an optional {@code reason} justification. Approval
 * (by a different user) is what actually posts the payment.
 */
@Data
public class SubmitTransportPaymentRequest {
    private Double amount;
    private String paymentMethod;
    private Term term;
    private Integer year;
    private Long vehicleId;
    private TransportType transportType;
    private String reason;
}
