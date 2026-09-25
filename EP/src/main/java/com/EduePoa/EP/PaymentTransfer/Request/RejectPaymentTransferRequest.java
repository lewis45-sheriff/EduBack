package com.EduePoa.EP.PaymentTransfer.Request;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RejectPaymentTransferRequest {
    private String reason;
}
