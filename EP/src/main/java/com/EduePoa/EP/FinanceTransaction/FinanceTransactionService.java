package com.EduePoa.EP.FinanceTransaction;

import com.EduePoa.EP.FinanceTransaction.Request.CreateTransactionDTO;
import com.EduePoa.EP.Utils.CustomResponse;

public interface FinanceTransactionService {
    CustomResponse<?>createTransaction(Long studentId,CreateTransactionDTO createTransactionDTO);
    CustomResponse<?> getTransactions();
    CustomResponse<?>getByStudentId(Long studentId);
}
