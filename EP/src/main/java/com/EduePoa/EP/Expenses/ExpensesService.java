package com.EduePoa.EP.Expenses;

import com.EduePoa.EP.Expenses.Requests.ExpenseRequestDTO;
import com.EduePoa.EP.Utils.CustomResponse;

import java.time.LocalDate;

public interface ExpensesService {

    CustomResponse<?> create(ExpenseRequestDTO requestDTO);

    CustomResponse<?> getAll(int page, int size, String sortBy, String sortDir,
                              String search, String category, String status,
                              String paymentMethod, LocalDate startDate, LocalDate endDate);

    CustomResponse<?> getById(Long id);

    CustomResponse<?> update(Long id, ExpenseRequestDTO requestDTO);

    CustomResponse<?> delete(Long id);

    CustomResponse<?> getSummary(LocalDate startDate, LocalDate endDate);

    CustomResponse<?> approveExpense(Long id);
}
