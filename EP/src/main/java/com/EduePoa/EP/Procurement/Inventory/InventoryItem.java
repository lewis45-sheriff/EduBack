package com.EduePoa.EP.Procurement.Inventory;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_items", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"item_name", "unit_of_measure"})
}, indexes = {
        @Index(name = "idx_inventory_item_name", columnList = "item_name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(length = 500)
    private String description;

    @Column(name = "unit_of_measure", nullable = false)
    private String unitOfMeasure;

    @Column(nullable = false)
    @Builder.Default
    private Integer currentQuantity = 0;

    @Column()
    @Builder.Default
    private Integer reorderLevel = 0;

    private LocalDateTime lastRestockedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
