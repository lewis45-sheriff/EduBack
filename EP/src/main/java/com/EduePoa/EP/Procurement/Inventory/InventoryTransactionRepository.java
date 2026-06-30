package com.EduePoa.EP.Procurement.Inventory;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InventoryTransactionRepository extends TenantAwareRepository<InventoryTransaction, Long> {
    Page<InventoryTransaction> findByInventoryItemIdOrderByCreatedAtDesc(Long inventoryItemId, Pageable pageable);
    boolean existsByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    // Reports: date-range stock movements
    List<InventoryTransaction> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime startDate, LocalDateTime endDate);
}
