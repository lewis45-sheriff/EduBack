package com.EduePoa.EP.HR.payroll.service;

import com.EduePoa.EP.HR.payroll.dto.request.ApproveRunRequest;
import com.EduePoa.EP.HR.payroll.dto.request.CreateRunRequest;
import com.EduePoa.EP.HR.payroll.dto.request.EmployeeAdjustmentRequest;
import com.EduePoa.EP.HR.payroll.dto.request.MarkPaidRequest;
import com.EduePoa.EP.HR.payroll.enums.PayrollStatus;
import com.EduePoa.EP.Utils.CustomResponse;

public interface PayrollService {

    CustomResponse<?> createRun(CreateRunRequest request);

    CustomResponse<?> processRun(Long runId);

    CustomResponse<?> approveRun(Long runId, ApproveRunRequest request);

    CustomResponse<?> rejectRun(Long runId, String reason);

    CustomResponse<?> markPaid(Long runId, MarkPaidRequest request);

    CustomResponse<?> listRuns(PayrollStatus status, int page, int size);

    CustomResponse<?> getRun(Long runId);

    CustomResponse<?> getRunPayslips(Long runId);

    CustomResponse<?> getPayslip(Long payslipId);

    /**
     * Renders a single payslip as a PDF. Returns the raw PDF bytes on success, or {@code null}
     * when the payslip does not exist (the controller maps that to 404).
     */
    byte[] getPayslipPdf(Long payslipId);

    /** Suggested download filename for a payslip PDF, or a generic fallback when unknown. */
    String getPayslipPdfFileName(Long payslipId);

    CustomResponse<?> getStatutorySummary(Long runId);

    CustomResponse<?> setEmployeeAdjustment(Long runId, EmployeeAdjustmentRequest request);

    CustomResponse<?> listEmployeeAdjustments(Long runId);
}
