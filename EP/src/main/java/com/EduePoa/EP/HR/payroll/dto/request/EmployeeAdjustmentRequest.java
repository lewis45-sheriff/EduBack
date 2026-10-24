package com.EduePoa.EP.HR.payroll.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * Set (upsert) a mid-period employment adjustment for one employee on a DRAFT/PROCESSED run.
 */
@Data
public class EmployeeAdjustmentRequest {

    @NotNull(message = "staffId is required")
    private Long staffId;

    private LocalDate joinDate;
    private LocalDate leaveDate;

    @Min(value = 0, message = "unpaidDays cannot be negative")
    private int unpaidDays = 0;
}
