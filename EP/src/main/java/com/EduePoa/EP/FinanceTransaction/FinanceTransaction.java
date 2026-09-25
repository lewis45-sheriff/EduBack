package com.EduePoa.EP.FinanceTransaction;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "finance_transactions")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class FinanceTransaction extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long studentId;

    @Column(nullable = false)
    private String studentName;

    private String admissionNumber;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType; // INCOME or EXPENSE

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate transactionDate;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private String reference; // Auto-generated or manual reference number

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
    @Column
    private Year year;
    @Column(name = "invoice_id")
    private Long invoiceId;
    @Enumerated(EnumType.STRING)
    private Term term;
    @Column(name = "opening_balance", precision = 10, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", precision = 10, scale = 2)
    private BigDecimal closingBalance;

    /**
     * How the transaction originated. Drives deletion eligibility: only MANUAL
     * transactions may be deleted; gateway (MPESA_*) payments are real money in and
     * can never be deleted (only transferred); PAYMENT_TRANSFER legs are undone by
     * reversing the transfer. Defaults to MANUAL when a caller does not set it.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TransactionSource source;

    /**
     * Web path (served under {@code /uploads/...}) of an optional supporting
     * document uploaded with a manual transaction (receipt / proof of payment).
     * Only the path is stored; the bytes live on disk.
     */
    @Column(name = "attachment_url")
    private String attachmentUrl;

    @PrePersist
    protected void onCreate() {
        // createdAt is NOT NULL in the DB. Populate it on insert unless the caller
        // already set it, otherwise the insert fails with "created_at cannot be null".
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (reference == null || reference.isEmpty()) {
            reference = generateReference();
        }
        if (source == null) {
            source = TransactionSource.MANUAL;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private String generateReference() {
        return "TXN" + System.currentTimeMillis();
    }

    public enum TransactionType {
        INCOME, EXPENSE
    }

    public enum PaymentMethod {
        CASH, MPESA, BANK, CHEQUE,OTHER,PAYPAL,EQUITEL,CARD
    }

    /** Where a transaction came from — governs whether it can be deleted. */
    public enum TransactionSource {
        /** Entered by a user through the finance UI; deletable. */
        MANUAL,
        /** Posted automatically from an M-Pesa C2B/paybill callback; NOT deletable. */
        MPESA_CALLBACK,
        /** Posted automatically from an M-Pesa STK push; NOT deletable. */
        MPESA_STK,
        /** One leg of a student-to-student payment transfer; undone via transfer reversal. */
        PAYMENT_TRANSFER,
        /** Any other system-generated origin; NOT deletable by default. */
        SYSTEM
    }
}