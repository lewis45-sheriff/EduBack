package com.EduePoa.EP.MPesa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MpesaCallbackDTO {
    private String merchantRequestID;
    private String checkoutRequestID;
    private String resultCode;
    private String resultDescription;
    private String mpesaReceiptNumber;
    private String transactionDate;
    private String phoneNumber;
}
