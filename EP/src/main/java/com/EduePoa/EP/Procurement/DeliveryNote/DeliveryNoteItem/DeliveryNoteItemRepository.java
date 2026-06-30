package com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNoteItem;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryNoteItemRepository extends TenantAwareRepository<DeliveryNoteItem, Long> {


    @Query("SELECT COALESCE(SUM(dni.deliveredQuantity), 0) FROM DeliveryNoteItem dni " +
            "WHERE dni.purchaseOrderItem.id = :poItemId")
    Integer getTotalDeliveredQuantityByPurchaseOrderItemId(@Param("poItemId") Long poItemId);
}
