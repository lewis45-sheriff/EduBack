package com.EduePoa.EP.MpesaPaybill.Requests;

import lombok.Data;

@Data
public class RegisterRequest {
//    private String shortCode;
//    private String responseType;
    private String confirmationURL;
    private String validationURL;
}
