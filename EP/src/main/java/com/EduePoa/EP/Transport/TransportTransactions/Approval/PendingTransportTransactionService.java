package com.EduePoa.EP.Transport.TransportTransactions.Approval;

import com.EduePoa.EP.Transport.TransportTransactions.Approval.Request.SubmitTransportPaymentRequest;
import com.EduePoa.EP.Utils.CustomResponse;

public interface PendingTransportTransactionService {

    /** Maker: submit a transport payment for approval. Stores a pending request only — no ledger write. */
    CustomResponse<?> submit(Long studentId, SubmitTransportPaymentRequest request);

    /** Checker: approve a pending payment — the only place a TransportTransactions row is written. */
    CustomResponse<?> approve(Long id);

    /** Checker: reject a pending payment. No ledger effect. */
    CustomResponse<?> reject(Long id, String reason);

    /** List pending payments, optionally filtered by status. */
    CustomResponse<?> list(PendingTransportTransactionStatus status);

    CustomResponse<?> get(Long id);
}
