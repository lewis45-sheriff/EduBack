package com.EduePoa.EP.FinanceTransaction;

import com.EduePoa.EP.FinanceTransaction.Request.CreateTransactionDTO;
import com.EduePoa.EP.Utils.CustomResponse;

public interface FinanceTransactionService {
    CustomResponse<?>createTransaction(Long studentId,CreateTransactionDTO createTransactionDTO);

    /**
     * Manual-creation entry point used by the (human) controller. Handles the
     * optional supporting-document attachment and the maker-checker flag; delegates
     * to {@link #createTransaction(Long, CreateTransactionDTO)} for the actual
     * posting when maker-checker is disabled. Automated callers (M-Pesa, bank
     * callbacks) must keep calling {@link #createTransaction(Long, CreateTransactionDTO)}
     * directly so they always post immediately.
     */
    CustomResponse<?> createManualTransaction(Long studentId, CreateTransactionDTO createTransactionDTO,
                                              org.springframework.web.multipart.MultipartFile attachment);

    /** Checker: approve a pending manual transaction — posts it to invoice/finance. */
    CustomResponse<?> approvePendingTransaction(Long pendingId);

    /** Checker: reject a pending manual transaction. No balances change. */
    CustomResponse<?> rejectPendingTransaction(Long pendingId, String reason);

    /** List pending manual transactions, optionally filtered by status. */
    CustomResponse<?> listPendingTransactions(
            com.EduePoa.EP.FinanceTransaction.PendingTransaction.PendingTransactionStatus status);
    CustomResponse<?> getTransactions();
    CustomResponse<?>getByStudentId(Long studentId);
    CustomResponse<?>getById(Long id);
    CustomResponse<?>getStatistics();
    CustomResponse<?> getStudentPayment(Long studentId);
    CustomResponse<?> getStudentBalance(Long studentId);
}
