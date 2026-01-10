package com.EduePoa.EP.BankIntergration.BankRequest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
public class BankRequestDTO {

    @JsonProperty("callbackType")
    private String callbackType;

    @JsonProperty("customer")
    private CustomerInfo customer;

    @JsonProperty("transaction")
    private TransactionInfo transaction;

    @JsonProperty("bank")
    private BankInfo bank;

    @Setter
    @Getter
    public static class CustomerInfo {
        @JsonProperty("name")
        private String name;

        @JsonProperty("mobileNumber")
        private String mobileNumber;

        @JsonProperty("reference")
        private String reference;
    }

    @Setter
    @Getter
    public static class TransactionInfo {
        @JsonProperty("date")
        private String date;

        @JsonProperty("reference")
        private String reference;

        @JsonProperty("paymentMode")
        private String paymentMode;

        @JsonProperty("amount")
        private BigDecimal amount;

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("billNumber")
        private String billNumber;

        @JsonProperty("servedBy")
        private String servedBy;

        @JsonProperty("additionalInfo")
        private String additionalInfo;

        @JsonProperty("orderAmount")
        private BigDecimal orderAmount;

        @JsonProperty("serviceCharge")
        private BigDecimal serviceCharge;

        @JsonProperty("orderCurrency")
        private String orderCurrency;

        @JsonProperty("status")
        private String status;

        @JsonProperty("remarks")
        private String remarks;
    }

    @Setter
    @Getter
    public static class BankInfo {
        @JsonProperty("reference")
        private String reference;

        @JsonProperty("transactionType")
        private String transactionType;

        @JsonProperty("account")
        private String account;
    }
}