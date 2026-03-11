package com.EduePoa.EP.Procurement.Inventory;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock_requisition_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockRequisitionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_requisition_id", nullable = false)
    private StockRequisition stockRequisition;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;
    @Column(nullable = false)
    private Integer requestedQuantity;
    @Column(length = 500)
    private String remarks;
}
