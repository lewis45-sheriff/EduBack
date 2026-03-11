package com.EduePoa.EP.Procurement.DeliveryNote;

import com.EduePoa.EP.Authentication.Enum.DeliveryNoteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryNoteRepository extends JpaRepository<DeliveryNote, Long> {

    List<DeliveryNote> findByPurchaseOrderId(Long purchaseOrderId);

    Page<DeliveryNote> findByStatus(DeliveryNoteStatus status, Pageable pageable);

    List<DeliveryNote> findByPurchaseOrderIdAndStatus(Long purchaseOrderId, DeliveryNoteStatus status);
}
