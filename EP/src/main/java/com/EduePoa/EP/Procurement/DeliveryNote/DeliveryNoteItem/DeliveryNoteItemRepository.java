package com.EduePoa.EP.Procurement.DeliveryNote.DeliveryNoteItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryNoteItemRepository extends JpaRepository<DeliveryNoteItem, Long> {


    @Query("SELECT COALESCE(SUM(dni.deliveredQuantity), 0) FROM DeliveryNoteItem dni " +
            "WHERE dni.purchaseOrderItem.id = :poItemId")
    Integer getTotalDeliveredQuantityByPurchaseOrderItemId(@Param("poItemId") Long poItemId);
}
