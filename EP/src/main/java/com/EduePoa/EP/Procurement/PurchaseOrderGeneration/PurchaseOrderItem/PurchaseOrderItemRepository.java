package com.EduePoa.EP.Procurement.PurchaseOrderGeneration.PurchaseOrderItem;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;

public interface PurchaseOrderItemRepository extends TenantAwareRepository<PurchaseOrderItem, Long> {
}
