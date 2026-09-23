package com.EduePoa.EP.Budget.Service;

import com.EduePoa.EP.Budget.DTO.Request.BudgetCategoryCreateRequest;
import com.EduePoa.EP.Budget.DTO.Request.BudgetCategoryUpdateRequest;
import com.EduePoa.EP.Budget.Enum.CategoryStatus;
import com.EduePoa.EP.Budget.Enum.CategoryType;
import com.EduePoa.EP.Utils.CustomResponse;

public interface BudgetCategoryService {

    CustomResponse<?> create(BudgetCategoryCreateRequest request);

    CustomResponse<?> update(Long id, BudgetCategoryUpdateRequest request);

    CustomResponse<?> getById(Long id);

    CustomResponse<?> getAll(int page, int size, String sortBy, String sortDir,
                             CategoryType type, CategoryStatus status, String search);

    /** Deactivate (soft delete) or physically delete if never used. */
    CustomResponse<?> delete(Long id);
}
