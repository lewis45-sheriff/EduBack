package com.EduePoa.EP.BankIntergration.BankRequest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@RequiredArgsConstructor
public class BankRequestDTO {

    @JsonProperty("MSISDN")
    private String msisdn;

    @JsonProperty("AccountNumber")
    private String accountNumber;

    @JsonProperty("TransID")
    private String transId;

    @JsonProperty("TransTime")
    private String transTime;

    @JsonProperty("TransAmount")
    private String transAmount;

    @JsonProperty("BusinessShortCode")
    private String businessShortCode;

    @JsonProperty("BillRefNumber")
    private String billRefNumber;

    @JsonProperty("InvoiceNumber")
    private String invoiceNumber;

    @JsonProperty("ThirdPartyTransID")
    private String thirdPartyTransId;

    @JsonProperty("TransactionType")
    private String transactionType;

    @JsonProperty("FirstName")
    private String firstName;

    @JsonProperty("MiddleName")
    private String middleName;

    @JsonProperty("LastName")
    private String lastName;

}