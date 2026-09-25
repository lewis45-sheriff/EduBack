package com.EduePoa.EP.TransactionReversal.Request;

import lombok.Data;

/** Checker request to reject a pending reversal, with an optional reason. */
@Data
public class RejectReversalRequest {
    private String reason;
}
