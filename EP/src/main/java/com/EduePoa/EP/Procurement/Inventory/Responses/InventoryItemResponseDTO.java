package com.EduePoa.EP.Procurement.Inventory.Responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItemResponseDTO {
    private Long id;
    private String itemName;
    private String description;
    private String unitOfMeasure;
    private Integer currentQuantity;
    private Integer reorderLevel;
    private LocalDateTime lastRestockedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
