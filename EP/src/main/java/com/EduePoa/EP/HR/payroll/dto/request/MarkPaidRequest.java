package com.EduePoa.EP.HR.payroll.dto.request;

import lombok.Data;

@Data
public class MarkPaidRequest {
    /** Optional payment reference (e.g. bank batch id) recorded with the payment status. */
    private String paymentReference;
}
