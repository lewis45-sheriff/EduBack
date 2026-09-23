package com.EduePoa.EP.Budget.Service;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Budget.DTO.Request.BudgetApprovalRequest;
import com.EduePoa.EP.Budget.DTO.Request.BudgetClosureRequest;
import com.EduePoa.EP.Budget.DTO.Request.BudgetCreateRequest;
import com.EduePoa.EP.Budget.DTO.Request.BudgetLineRequest;
import com.EduePoa.EP.Budget.DTO.Request.BudgetUpdateRequest;
import com.EduePoa.EP.Budget.Enum.BudgetStatus;
import com.EduePoa.EP.Utils.CustomResponse;

import java.time.LocalDate;
import java.time.Year;

public interface BudgetService {

    CustomResponse<?> create(BudgetCreateRequest request);

    CustomResponse<?> update(Long id, BudgetUpdateRequest request);

    CustomResponse<?> getById(Long id);

    CustomResponse<?> getAll(int page, int size, String sortBy, String sortDir,
                             BudgetStatus status, Year academicYear, Term academicTerm,
                             LocalDate startDate, LocalDate endDate, String search);

    CustomResponse<?> delete(Long id);

    // Line management (DRAFT only)
    CustomResponse<?> addLine(Long budgetId, BudgetLineRequest request);

    CustomResponse<?> updateLine(Long budgetId, Long lineId, BudgetLineRequest request);

    CustomResponse<?> removeLine(Long budgetId, Long lineId);

    // Lifecycle
    CustomResponse<?> approve(Long id, BudgetApprovalRequest request);

    CustomResponse<?> close(Long id, BudgetClosureRequest request);
}
