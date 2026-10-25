package com.EduePoa.EP.MpesaPaybill.Response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Read model for the M-Pesa C2B transactions dashboard
 * ({@code GET /api/v1/mpesa-c2b/all-transactions}).
 * <p>
 * Shaped to match the frontend {@code MpesaTransaction} interface so the UI's
 * {@code normalizeModernTransaction} mapper consumes it without changes. The
 * underlying rows come from {@code mpesa_paybill}; reconciliation state is derived
 * by matching a {@code FinanceTransaction} whose {@code reference} equals the
 * M-Pesa {@code TransID}.
 */
@Data
@Builder
public class MpesaC2bTransactionResponse {
    private String internalId;
    private String transactionId;
    private String transactionType;
    private String transID;
    private String transTime;
    private double transAmount;
    private String businessShortCode;
    private String billRefNumber;
    private String invoiceNumber;
    private double orgAccountBalance;
    private String thirdPartyTransID;
    private String msisdn;
    private String firstName;
    private String middleName;
    private String lastName;
    @JsonProperty("isReconciled")
    private boolean isReconciled;
    private String studentId;
    private String reconciledAt;
    private String reconciledBy;
}
