package com.EduePoa.EP.HR.payroll.dto.request;

import lombok.Data;

@Data
public class ApproveRunRequest {
    /** Optional free-text comment recorded with the approval. */
    private String comment;
}
