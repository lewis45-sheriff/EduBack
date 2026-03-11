package com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNoteItem;

import com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNote;
import com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrderItem.PurchaseOrderItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "delivery_note_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryNoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_note_id", nullable = false)
    private DeliveryNote deliveryNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_item_id", nullable = false)
    private PurchaseOrderItem purchaseOrderItem;

    @Column(nullable = false)
    private Integer deliveredQuantity;

    @Column(length = 500)
    private String remarks;
}