package com.EduePoa.EP.TransactionReversal.Request;

import lombok.Data;

/**
 * Maker request to reverse a finance transaction. The transaction id is supplied
 * on the path; the body carries the mandatory business reason for the reversal.
 */
@Data
public class CreateReversalRequest {
    private String reason;
}
