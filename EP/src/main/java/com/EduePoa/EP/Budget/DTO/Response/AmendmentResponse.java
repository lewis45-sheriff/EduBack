package com.EduePoa.EP.Budget.DTO.Response;

import com.EduePoa.EP.Budget.Enum.AmendmentStatus;
import com.EduePoa.EP.Budget.Enum.AmendmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmendmentResponse {
    private Long id;
    private Long budgetId;
    private Long budgetLineId;
    private Long categoryId;
    private String categoryName;
    private Long orgUnitId;
    private String orgUnitName;
    private AmendmentType type;
    private BigDecimal originalAmount;
    private BigDecimal requestedDelta;
    private BigDecimal proposedAmount;
    private BigDecimal finalApprovedAmount;
    private String reason;
    private String notes;
    private String rejectionReason;
    private AmendmentStatus status;
    private String requestedByName;
    private String approvedByName;
    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt;
    private LocalDateTime appliedAt;
}
