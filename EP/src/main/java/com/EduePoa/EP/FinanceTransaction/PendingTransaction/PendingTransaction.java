package com.EduePoa.EP.FinanceTransaction.PendingTransaction;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.FinanceTransaction.FinanceTransaction;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;

/**
 * A manually-entered finance transaction captured as a maker-checker request.
 * <p>
 * Only created when {@code finance.transaction.maker-checker.enabled=true}. It
 * holds all the data needed to post the real {@link FinanceTransaction} on
 * approval; nothing touches invoices/finance until a DIFFERENT user approves.
 */
@Entity
@Table(name = "pending_transactions")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PendingTransaction extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    private String studentName;
    private String admissionNumber;

    @Enumerated(EnumType.STRING)
    private FinanceTransaction.TransactionType transactionType;

    private String category;
    private BigDecimal amount;
    private LocalDate transactionDate;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    private FinanceTransaction.PaymentMethod paymentMethod;

    private String reference;

    @Enumerated(EnumType.STRING)
    private Term term;

    private Year year;
    private Long invoiceId;

    /** Web path of the optional supporting document uploaded with the request. */
    private String attachmentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PendingTransactionStatus status = PendingTransactionStatus.PENDING_APPROVAL;

    // ---- Maker / checker ------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private LocalDateTime approvedAt;

    /** Id of the FinanceTransaction created when this request was approved. */
    private Long postedTransactionId;

    @Column(length = 500)
    private String rejectionReason;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = PendingTransactionStatus.PENDING_APPROVAL;
        }
    }
}
