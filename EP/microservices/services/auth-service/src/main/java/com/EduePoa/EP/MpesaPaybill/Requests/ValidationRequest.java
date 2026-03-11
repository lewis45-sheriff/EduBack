package com.EduePoa.EP.MpesaPaybill.Requests;

import lombok.Data;

@Data
public class ValidationRequest {
    private String TransactionType;
    private String TransID;
    private String TransTime;
    private String TransAmount;
    private String BusinessShortCode;
    private String BillRefNumber;
    private String InvoiceNumber;
    private String OrgAccountBalance;
    private String ThirdPartyTransID;
    private String MSISDN;
    private String FirstName;
    private String MiddleName;
    private String LastName;
}
