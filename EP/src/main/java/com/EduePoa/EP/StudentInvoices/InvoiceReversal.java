package com.EduePoa.EP.StudentInvoices;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;

/**
 * Audit record of a reversed (voided) invoice.
 * <p>
 * Because reversing an invoice physically removes the {@link StudentInvoices}
 * row (to free the unique student/term/year key for re-invoicing), this entity
 * preserves a snapshot of the invoice as it was at the moment of reversal, plus
 * who performed it and how it was scoped (single, student, grade, school-wide).
 * These records are what the frontend lists as reversal history.
 */
@Entity
@Table(name = "invoice_reversals")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InvoiceReversal extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The id the invoice had before it was removed. */
    private Long originalInvoiceId;

    private Long studentId;
    private String studentName;
    private String admissionNumber;
    private String grade;

    @Enumerated(EnumType.STRING)
    private Term term;

    private Year academicYear;

    /** Invoice figures captured at reversal time. */
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private BigDecimal balance;

    /** The invoice's own dates, snapshotted. */
    private LocalDate invoiceDate;
    private LocalDate dueDate;

    /** How the reversal was triggered: SINGLE, STUDENT, GRADE, SCHOOL_WIDE. */
    private String scope;

    /** How many optional-fee assignments were released back for re-invoicing. */
    private int releasedOptionalFees;

    /** Email/username of the user who performed the reversal. */
    private String reversedBy;

    private LocalDateTime reversedAt;

    @PrePersist
    void onCreate() {
        if (reversedAt == null) {
            reversedAt = LocalDateTime.now();
        }
        if (scope == null) {
            scope = "SINGLE";
        }
    }
}
