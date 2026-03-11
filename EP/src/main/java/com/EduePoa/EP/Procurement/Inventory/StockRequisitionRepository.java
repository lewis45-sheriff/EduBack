package com.EduePoa.EP.Procurement.Inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockRequisitionRepository extends JpaRepository<StockRequisition, Long> {}

