package com.EduePoa.EP.StudentRegistration.OptionalFees.Request;

import com.EduePoa.EP.Authentication.Enum.Term;
import lombok.Data;

import java.time.Year;

/**
 * Client-controlled payload for assigning an optional fee. The optional item is
 * a fee-structure line item ({@code FeeComponentConfig}). It deliberately
 * excludes trusted security data (tenantId, assignedByUserId, assignedBy,
 * amount) which are all resolved server-side.
 */
@Data
public class StudentOptionalFeeAssignRequest {
    private Long studentId;
    /** Id of the fee-structure line item (FeeComponentConfig) to assign. */
    private Long feeComponentConfigId;
    /**
     * Optional. When omitted, the line item's own term is used. When provided it
     * must match the line item's term.
     */
    private Term term;
    private Year academicYear;
}
