package com.EduePoa.EP.PaymentTransfer;

import com.EduePoa.EP.PaymentTransfer.Request.CreatePaymentTransferRequest;
import com.EduePoa.EP.Utils.CustomResponse;

public interface PaymentTransferService {

    /** Maker: store a pending transfer request. No balances are touched. */
    CustomResponse<?> createTransfer(CreatePaymentTransferRequest request);

    /** Checker: approve a pending transfer, applying it to both students. */
    CustomResponse<?> approveTransfer(Long id);

    /** Checker: reject a pending transfer with a reason. No balances are touched. */
    CustomResponse<?> rejectTransfer(Long id, String reason);

    /** List transfers, optionally filtered by status (newest first). */
    CustomResponse<?> listTransfers(PaymentTransferStatus status);

    /** Get a single transfer by id. */
    CustomResponse<?> getTransfer(Long id);
}
