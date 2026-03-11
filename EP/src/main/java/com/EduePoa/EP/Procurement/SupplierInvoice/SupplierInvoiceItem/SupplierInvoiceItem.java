package com.EduePoa.EP.Procurement.SupplierInvoice.SupplierInvoiceItem;

import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrderItem.PurchaseOrderItem;
import com.EduePoa.EP.Procurement.SupplierInvoice.SupplierInvoice;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "supplier_invoice_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierInvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "invoice_id", nullable = false)
    private SupplierInvoice supplierInvoice;

    @ManyToOne
    @JoinColumn(name = "po_item_id", nullable = false)
    private PurchaseOrderItem purchaseOrderItem;

    @Column(nullable = false)
    private Integer invoicedQuantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPrice;
}