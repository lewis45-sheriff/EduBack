package com.EduePoa.EP.Procurement.SupplierPayments;

import com.EduePoa.EP.Authentication.Enum.PaymentMethod;
import com.EduePoa.EP.Authentication.Enum.PaymentStatus;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Procurement.SupplierInvoice.SupplierInvoice;
import com.EduePoa.EP.Procurement.SupplierOnboarding.SupplierOnboarding;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "supplier_payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
//@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "supplier_invoice_id", referencedColumnName = "id", nullable = false)
    private SupplierInvoice supplierInvoice;

    @ManyToOne
    @JoinColumn(name = "supplier_id", nullable = false)
    private SupplierOnboarding supplier;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Column(length = 100)
    private String referenceNumber;

    @Column(length = 500)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING_APPROVAL;

    @Column(columnDefinition = "TEXT")
    private String pendingPaymentData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private LocalDateTime approvedAt;


}
