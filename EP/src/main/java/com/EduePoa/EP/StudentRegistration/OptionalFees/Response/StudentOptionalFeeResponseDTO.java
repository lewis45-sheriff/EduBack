package com.EduePoa.EP.StudentRegistration.OptionalFees.Response;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.StudentRegistration.OptionalFees.AssignedBy;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;

/**
 * Read model for optional-fee assignments returned to API consumers. Exposes
 * only the fields needed by the frontend and omits internal flags.
 */
@Data
@Builder
public class StudentOptionalFeeResponseDTO {
    private Long id;
    private Long studentId;
    private String studentName;
    /** Id of the fee-structure line item (FeeComponentConfig) that was assigned. */
    private Long feeComponentConfigId;
    private String feeComponentName;
    /** Id of the fee structure the line item belongs to. */
    private Long feeStructureId;
    private BigDecimal amount;
    private Term term;
    private Year academicYear;
    private AssignedBy assignedBy;
    private LocalDateTime assignedAt;
    /** "ACTIVE" or "REMOVED". */
    private String status;
    /** Whether this assignment has already been rolled into a generated invoice. */
    private boolean invoiced;
}
