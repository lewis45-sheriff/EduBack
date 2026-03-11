package com.EduePoa.EP.Procurement.Inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    Optional<InventoryItem> findByItemNameIgnoreCaseAndUnitOfMeasureIgnoreCase(String itemName, String unitOfMeasure);
    Page<InventoryItem> findByCurrentQuantityLessThanEqual(Integer threshold, Pageable pageable);

    // Reports: full list (no pagination) for PDF generation
    List<InventoryItem> findByCurrentQuantityLessThanEqual(Integer threshold);
}
