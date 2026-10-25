package com.EduePoa.EP.Budget.DTO.Request;

import com.EduePoa.EP.Budget.Enum.AmendmentType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Submit a controlled amendment to an approved budget.
 * <ul>
 *   <li>INCREASE / DECREASE: provide {@code budgetLineId} and a positive {@code amount}
 *       (the magnitude of the change; the sign is derived from the type).</li>
 *   <li>SUPPLEMENTARY_LINE: provide {@code categoryId} (and optional {@code orgUnitId}) and a
 *       positive {@code amount} for the new allocation; {@code budgetLineId} must be null.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmendmentSubmitRequest {

    @NotNull(message = "type is required")
    private AmendmentType type;

    /** Required for INCREASE/DECREASE; must be null for SUPPLEMENTARY_LINE. */
    private Long budgetLineId;

    /** Required for SUPPLEMENTARY_LINE. */
    private Long categoryId;

    /** Optional org unit for SUPPLEMENTARY_LINE (null = school-wide). */
    private Long orgUnitId;

    /** Positive magnitude of the change / the new line amount. */
    @NotNull(message = "amount is required")
    private BigDecimal amount;

    private String reason;

    private String notes;
}
