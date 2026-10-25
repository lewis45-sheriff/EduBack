package com.EduePoa.EP.Budget.Service;

import com.EduePoa.EP.Budget.DTO.Request.AmendmentDecisionRequest;
import com.EduePoa.EP.Budget.DTO.Request.AmendmentSubmitRequest;
import com.EduePoa.EP.Utils.CustomResponse;

public interface BudgetAmendmentService {

    CustomResponse<?> submit(Long budgetId, AmendmentSubmitRequest request);

    CustomResponse<?> approve(Long amendmentId, AmendmentDecisionRequest request);

    CustomResponse<?> reject(Long amendmentId, AmendmentDecisionRequest request);

    CustomResponse<?> getById(Long amendmentId);

    CustomResponse<?> history(Long budgetId, int page, int size);
}
