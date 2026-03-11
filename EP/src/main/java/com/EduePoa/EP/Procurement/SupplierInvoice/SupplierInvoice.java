package com.EduePoa.EP.Procurement.SupplierInvoice;


import com.EduePoa.EP.Authentication.Enum.InvoiceStatus;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNote;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrder;
import com.EduePoa.EP.Procurement.SupplierInvoice.SupplierInvoiceItem.SupplierInvoiceItem;
import com.EduePoa.EP.Procurement.SupplierOnboarding.SupplierOnboarding;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "supplier_invoices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
//@EntityListeners(AuditingEntityListener.class)
public class SupplierInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", referencedColumnName = "id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToMany
    @JoinTable(name = "supplier_invoice_delivery_notes", joinColumns = @JoinColumn(name = "supplier_invoice_id"), inverseJoinColumns = @JoinColumn(name = "delivery_note_id"))
    private List<DeliveryNote> deliveryNotes;

    @ManyToOne
    @JoinColumn(name = "supplier_id", nullable = false)
    private SupplierOnboarding supplier;

    @Column(nullable = false, unique = true)
    private String invoiceNumber;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal vatAmount;

    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2)
    private BigDecimal outstandingBalance;

    // ETIMS compliance field
    @Column(length = 50)
    private String supplierTIN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.UPLOADED;

    @Lob
    private String invoiceDocument;

    @OneToMany(mappedBy = "supplierInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SupplierInvoiceItem> items;

    @Column(length = 500)
    private String rejectionReason;

    // Audit fields
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    private User rejectedBy;

    private LocalDateTime rejectedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    private LocalDateTime updatedAt;
}
