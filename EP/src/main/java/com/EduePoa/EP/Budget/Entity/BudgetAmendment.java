package com.EduePoa.EP.Budget.Entity;

import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Budget.Enum.AmendmentStatus;
import com.EduePoa.EP.Budget.Enum.AmendmentType;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.OrgUnit.Entity.OrgUnit;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An audited, controlled change to an APPROVED budget (design §9). An amendment is the only way to
 * change allocations on an approved budget; it goes through submit → approve/reject → atomic apply.
 *
 * <p>Amount model (one coherent set, no contradictory redundancy):
 * <ul>
 *   <li>{@link #originalAmount}: the target line's allocation at submission (null for a
 *       SUPPLEMENTARY_LINE, which has no existing line).</li>
 *   <li>{@link #requestedDelta}: the signed change the requester proposes. For INCREASE it is
 *       positive, for DECREASE negative, for SUPPLEMENTARY_LINE it is the new line's full amount.</li>
 *   <li>{@link #proposedAmount}: the resulting allocation if applied ({@code originalAmount +
 *       requestedDelta}, or {@code requestedDelta} for a supplementary line). Stored for auditability.</li>
 *   <li>{@link #finalApprovedAmount}: the allocation actually written when the amendment is applied.
 *       Set only on APPROVED+applied amendments.</li>
 * </ul>
 */
@Entity
@Table(name = "budget_amendments", indexes = {
        @Index(name = "idx_budget_amendment_budget", columnList = "tenant_id, budget_id"),
        @Index(name = "idx_budget_amendment_status", columnList = "tenant_id, status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class BudgetAmendment extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    /** Existing line being amended; null for a SUPPLEMENTARY_LINE amendment. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_line_id")
    private BudgetLine budgetLine;

    // ---- For SUPPLEMENTARY_LINE: the new allocation's dimensions ----
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private BudgetCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_unit_id")
    private OrgUnit orgUnit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AmendmentType type;

    @Column(name = "original_amount", precision = 19, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "requested_delta", nullable = false, precision = 19, scale = 2)
    private BigDecimal requestedDelta;

    @Column(name = "proposed_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal proposedAmount;

    @Column(name = "final_approved_amount", precision = 19, scale = 2)
    private BigDecimal finalApprovedAmount;

    @Column(length = 1000)
    private String reason;

    @Column(length = 1000)
    private String notes;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AmendmentStatus status = AmendmentStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Version
    private Long version;
}
