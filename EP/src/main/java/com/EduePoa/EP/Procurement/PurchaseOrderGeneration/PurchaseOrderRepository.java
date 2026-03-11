package com.EduePoa.EP.Procurement.PurchaseOrderGeneration;

import com.EduePoa.EP.Authentication.Enum.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Page<PurchaseOrder> findByStatus(PurchaseOrderStatus status, Pageable pageable);

    List<PurchaseOrder> findBySupplierId(Long supplierId);
}
