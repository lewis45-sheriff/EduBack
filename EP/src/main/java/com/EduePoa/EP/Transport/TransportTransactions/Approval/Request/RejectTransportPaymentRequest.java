package com.EduePoa.EP.Transport.TransportTransactions.Approval.Request;

import lombok.Data;

/** Checker's rejection of a pending transport payment, with an optional reason. */
@Data
public class RejectTransportPaymentRequest {
    private String reason;
}
