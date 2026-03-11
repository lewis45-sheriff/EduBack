package com.EduePoa.EP.Procurement.Inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
    Page<InventoryTransaction> findByInventoryItemIdOrderByCreatedAtDesc(Long inventoryItemId, Pageable pageable);
    boolean existsByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    // Reports: date-range stock movements
    List<InventoryTransaction> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime startDate, LocalDateTime endDate);
}
