package com.EduePoa.EP.TransactionReversal;

import com.EduePoa.EP.Utils.CustomResponse;

public interface TransactionReversalService {

    /** Maker: request reversal of a finance transaction. Stores a pending request only. */
    CustomResponse<?> createReversal(Long financeTransactionId, String reason);

    /** Checker: approve a pending reversal — the only place balances are restored. */
    CustomResponse<?> approveReversal(Long id);

    /** Checker: reject a pending reversal. No financial effect. */
    CustomResponse<?> rejectReversal(Long id, String reason);

    CustomResponse<?> listReversals(TransactionReversalStatus status);

    CustomResponse<?> getReversal(Long id);
}
