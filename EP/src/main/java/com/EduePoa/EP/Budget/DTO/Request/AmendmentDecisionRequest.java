package com.EduePoa.EP.Budget.DTO.Request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Approve or reject a pending amendment. {@code reason} is required when rejecting and recorded as
 * the rejection reason.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmendmentDecisionRequest {
    private String reason;
}
