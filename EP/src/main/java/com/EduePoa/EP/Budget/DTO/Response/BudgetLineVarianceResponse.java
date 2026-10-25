package com.EduePoa.EP.Budget.DTO.Response;

import com.EduePoa.EP.Budget.Enum.CategoryType;
import com.EduePoa.EP.Budget.Enum.PerformanceStatus;
import com.EduePoa.EP.Budget.Variance.MappingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Canonical per-line budget-vs-actual figures. Produced by the single calculation layer
 * ({@code BudgetVarianceCalculator}) and reused unchanged by the budget detail view, the
 * dashboard, reports, and exports so a figure never changes between screens.
 *
 * <p>Sign conventions (see design §7):
 * <ul>
 *   <li>EXPENSE: {@code varianceAmount = approvedAmount - actualAmount} (positive = under budget);
 *       {@code availableAmount = approvedAmount - actualAmount - committedAmount}.</li>
 *   <li>INCOME: {@code varianceAmount = actualAmount - approvedAmount} (positive = above target).
 *       Income has no "available to spend"; {@link #availableAmount} is null for income and the
 *       target gap is in {@link #incomeRemainingToTarget}.</li>
 * </ul>
 * {@link #utilizationPercent} is {@code actual/approved*100} at scale 2 HALF_UP, or null when
 * the approved amount is zero (a non-zero actual against a zero budget is flagged via
 * {@link #unbudgeted} and an OVER / ABOVE status rather than a division by zero).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetLineVarianceResponse {

    private Long lineId;

    private Long categoryId;
    private String categoryName;
    private CategoryType categoryType;

    /** Optional organizational unit this allocation targets; null = school-wide / unassigned. */
    private Long orgUnitId;
    private String orgUnitName;

    /** Current approved baseline (original + applied amendments). For a DRAFT budget this is the draft amount. */
    private BigDecimal approvedAmount;

    /** Amount originally approved, before any amendments. Equals approvedAmount until an amendment is applied. */
    private BigDecimal originalApprovedAmount;

    /** Net posted GL activity attributable to this line's mapped account over the budget window. */
    private BigDecimal actualAmount;

    /**
     * Separately tracked approved commitments. Phase 1 has no commitment source, so this is a
     * documented zero with {@link #committedSupported} = false (NOT a verified zero commitment).
     */
    private BigDecimal committedAmount;
    private boolean committedSupported;

    /** EXPENSE only: approvedAmount - actualAmount - committedAmount. Null for INCOME. */
    private BigDecimal availableAmount;

    /** Signed variance per the type's convention. */
    private BigDecimal varianceAmount;

    /** INCOME only: approvedAmount - actualAmount, floored at zero (how much target is still to be earned). Null for EXPENSE. */
    private BigDecimal incomeRemainingToTarget;

    /** actual/approved*100 (scale 2, HALF_UP); null when approvedAmount is zero. */
    private BigDecimal utilizationPercent;

    /** True when approvedAmount is zero but actualAmount is non-zero (unbudgeted activity). */
    private boolean unbudgeted;

    private MappingStatus mappingStatus;

    /** The GL account systemKey this line resolved to, when mapped; else null. */
    private String mappingKey;

    private PerformanceStatus performanceStatus;
}
