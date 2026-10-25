package com.EduePoa.EP.GeneralLedger.entity;

import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.GeneralLedger.enums.GlReversalStatus;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Maker-checker request to reverse a {@link JournalEntry}, modelled on the
 * existing {@code TransactionReversal}. The request stores an immutable snapshot
 * of the target journal (number, date, description, totals) and the reason, so
 * the history survives independently of the target. Approval creates exactly one
 * counter-journal; rejection leaves the original untouched.
 * <p>
 * {@code @Version} guards the PENDING_APPROVAL → APPROVED/REJECTED transition so
 * two simultaneous approvals cannot both succeed.
 */
@Entity
@Table(name = "gl_reversal_request",
        indexes = {
                @Index(name = "idx_gl_reversal_target", columnList = "tenant_id, target_journal_id"),
                @Index(name = "idx_gl_reversal_status", columnList = "tenant_id, status")
        })
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlReversalRequest extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_journal_id", nullable = false)
    private Long targetJournalId;

    // ---- Immutable snapshot of the target journal (captured at request time) ----

    @Column(name = "target_entry_number", length = 40)
    private String targetEntryNumber;

    @Column(name = "target_description", length = 500)
    private String targetDescription;

    @Column(name = "target_total", precision = 19, scale = 2)
    private BigDecimal targetTotal;

    @Column(name = "target_source_type", length = 40)
    private String targetSourceType;

    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private GlReversalStatus status = GlReversalStatus.PENDING_APPROVAL;

    /** Set to the counter-journal id once the reversal is approved and applied. */
    @Column(name = "counter_journal_id")
    private Long counterJournalId;

    // ---- Maker / checker ----

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = GlReversalStatus.PENDING_APPROVAL;
        }
    }
}
