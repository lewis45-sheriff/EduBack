package com.EduePoa.EP.HR.compensation.service;

import com.EduePoa.EP.HR.compensation.dto.request.AllowanceRequest;
import com.EduePoa.EP.HR.compensation.dto.request.CompensationRequest;
import com.EduePoa.EP.HR.compensation.dto.request.RecurringDeductionRequest;
import com.EduePoa.EP.Utils.CustomResponse;

public interface CompensationService {

    CustomResponse<?> saveCompensation(CompensationRequest request);

    CustomResponse<?> getByStaffId(Long staffId);

    CustomResponse<?> list(int page, int size);

    CustomResponse<?> addAllowance(Long staffId, AllowanceRequest request);

    CustomResponse<?> deleteAllowance(Long staffId, Long allowanceId);

    CustomResponse<?> addDeduction(Long staffId, RecurringDeductionRequest request);

    CustomResponse<?> deleteDeduction(Long staffId, Long deductionId);
}
