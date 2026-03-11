package com.EduePoa.EP.Procurement.Inventory;

import com.EduePoa.EP.Authentication.User.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_transactions", indexes = {
        @Index(name = "idx_inv_txn_item", columnList = "inventory_item_id"),
        @Index(name = "idx_inv_txn_ref", columnList = "reference_type, reference_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;
    @Column(nullable = false, length = 20)
    private String transactionType; // STOCK_IN, STOCK_OUT
    @Column(nullable = false)
    private Integer quantity;
    @Column(nullable = false)
    private Integer previousQuantity;
    @Column(nullable = false)
    private Integer newQuantity;
    @Column(nullable = false, length = 30)
    private String referenceType; // DELIVERY_NOTE, STOCK_REQUISITION
    @Column(nullable = false)
    private Long referenceId;
    @Column(length = 500)
    private String remarks;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
