package com.EduePoa.EP.TransactionReversal;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.FinanceTransaction.FinanceTransaction;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;


@Entity
@Table(name = "transaction_reversals")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionReversal extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id of the {@link FinanceTransaction} being reversed (snapshot; the row may be deleted on approval). */
    @Column(nullable = false)
    private Long financeTransactionId;

    // ---- Snapshot of the target transaction (captured at request time) ----

    private Long studentId;
    private String studentName;
    private Long invoiceId;

    @Enumerated(EnumType.STRING)
    private FinanceTransaction.TransactionType transactionType;

    private String category;
    private BigDecimal amount;
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    private Term term;

    private Year academicYear;

    // ---- Transfer linkage (set when the target transaction came from a payment transfer) ----

    /** True when the target transaction is a leg of a student-to-student payment transfer. */
    @Builder.Default
    private boolean transferOriginated = false;

    /** Id of the {@link com.EduePoa.EP.PaymentTransfer.PaymentTransfer} this transaction belongs to, when resolvable. */
    private Long paymentTransferId;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransactionReversalStatus status = TransactionReversalStatus.PENDING_APPROVAL;

    // ---- Maker / checker ------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private LocalDateTime approvedAt;

    /** Set when the reversal is approved and the balances are actually restored. */
    private LocalDateTime appliedAt;

    @Column(length = 500)
    private String rejectionReason;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = TransactionReversalStatus.PENDING_APPROVAL;
        }
    }
}
