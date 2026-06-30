package com.EduePoa.EP.Procurement.Inventory;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockRequisitionRepository extends TenantAwareRepository<StockRequisition, Long> {}

