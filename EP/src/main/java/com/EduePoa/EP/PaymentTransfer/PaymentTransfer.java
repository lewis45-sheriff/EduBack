package com.EduePoa.EP.PaymentTransfer;

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

/**
 * A maker-checker request to move part or all of a recorded payment from one
 * student to another.
 * <p>
 * Created as {@link PaymentTransferStatus#PENDING_APPROVAL} and stored only — no
 * invoice, finance, or transaction is touched at creation. All financial effects
 * happen on approval. The source of truth for the amount is a
 * {@link FinanceTransaction} identified by its unique {@code sourcePaymentReference}
 * (e.g. an M-Pesa receipt); the transferable amount for a reference is the
 * original payment amount minus the amounts already committed by other
 * PENDING_APPROVAL/APPROVED transfers for the same reference.
 */
@Entity
@Table(name = "payment_transfers")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentTransfer extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique reference of the source payment (e.g. M-Pesa receipt code). */
    @Column(nullable = false)
    private String sourcePaymentReference;

    /** Id of the source {@link FinanceTransaction} the reference resolved to. */
    private Long sourceTransactionId;

    /** The full amount of the source payment, snapshotted at request time. */
    private BigDecimal originalPaymentAmount;

    /** The amount to move (may be less than the original for partial transfers). */
    @Column(nullable = false)
    private BigDecimal amount;

    // Source side (derived from the payment transaction).
    private Long sourceStudentId;
    private String sourceStudentName;
    private Long sourceInvoiceId;

    // Destination side (supplied by the maker).
    private Long destStudentId;
    private String destStudentName;
    private Long destInvoiceId;

    /** Term/year of the transfer, derived from the source payment. */
    @Enumerated(EnumType.STRING)
    private Term term;

    private Year academicYear;

    @Enumerated(EnumType.STRING)
    private FinanceTransaction.PaymentMethod paymentMethod;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentTransferStatus status = PaymentTransferStatus.PENDING_APPROVAL;

    // ---- Maker / checker ------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private LocalDateTime approvedAt;

    /** Set when the transfer is approved and the balances are actually moved. */
    private LocalDateTime appliedAt;

    @Column(length = 500)
    private String rejectionReason;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = PaymentTransferStatus.PENDING_APPROVAL;
        }
    }
}
