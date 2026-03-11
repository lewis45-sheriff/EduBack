package com.EduePoa.EP.BankIntergration.BankResponse;

import lombok.Data;

@Data
public class StkPushResponseDto {
    private String transactionID;
    private String statusCode;
    private String statusDescription;
    private String referenceID;
}
