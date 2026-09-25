package com.EduePoa.EP.StudentRegistration.OptionalFees.Request;

import com.EduePoa.EP.Authentication.Enum.Term;
import lombok.Data;

import java.time.Year;
import java.util.List;

/**
 * Client-controlled payload for assigning several optional fee line items to one
 * student in a single call. Each id in {@link #feeComponentConfigIds} is a
 * fee-structure line item ({@code FeeComponentConfig}).
 * <p>
 * Trusted security data (tenantId, actor, amount) is resolved server-side and is
 * never accepted from the client.
 */
@Data
public class StudentOptionalFeeBatchAssignRequest {
    private Long studentId;
    /** Ids of the fee-structure line items to assign. */
    private List<Long> feeComponentConfigIds;
    /**
     * Optional. When omitted, each line item's own term is used. When provided it
     * must match each selected line item's term.
     */
    private Term term;
    private Year academicYear;
}
