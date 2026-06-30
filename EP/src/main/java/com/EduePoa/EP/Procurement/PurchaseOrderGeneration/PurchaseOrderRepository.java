package com.EduePoa.EP.Procurement.PurchaseOrderGeneration;

import com.EduePoa.EP.Authentication.Enum.PurchaseOrderStatus;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderRepository extends TenantAwareRepository<PurchaseOrder, Long> {

    Page<PurchaseOrder> findByStatus(PurchaseOrderStatus status, Pageable pageable);

    List<PurchaseOrder> findBySupplierId(Long supplierId);

    Page<PurchaseOrder> findBySupplierId(Long supplierId, Pageable pageable);
}
