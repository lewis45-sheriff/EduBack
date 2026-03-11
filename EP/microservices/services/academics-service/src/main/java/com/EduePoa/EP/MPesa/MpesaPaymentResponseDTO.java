package com.EduePoa.EP.MPesa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MpesaPaymentResponseDTO {
    private String MerchantRequestID;
    private String CheckoutRequestID;
    private String ResponseCode;
    private String ResponseDescription;
    private String CustomerMessage;
}

