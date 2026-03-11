package com.EduePoa.EP.Procurement.SupplierInvoice;

import com.EduePoa.EP.Authentication.Enum.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long> {

    // Existing methods
    // Note: findByInvoiceNumber from original is implicitly replaced by
    // existsByInvoiceNumber for uniqueness checks
    boolean existsByInvoiceNumber(String invoiceNumber);

    @Query("SELECT si FROM SupplierInvoice si WHERE si.supplier.id = :supplierId")
    List<SupplierInvoice> findBySupplier(@Param("supplierId") Long supplierId);

    Page<SupplierInvoice> findByStatus(InvoiceStatus status, Pageable pageable);

    List<SupplierInvoice> findBySupplierId(Long supplierId);

    // Re-adding original methods that were not explicitly replaced by multi-tenancy
    // versions
    Optional<SupplierInvoice> findByInvoiceNumber(String invoiceNumber); // Kept for non-multi-tenancy lookup if needed

    List<SupplierInvoice> findByPurchaseOrderId(Long purchaseOrderId);

    List<SupplierInvoice> findByStatus(InvoiceStatus status);

    // Reports: outstanding invoices (not fully paid)
    List<SupplierInvoice> findByStatusNot(InvoiceStatus status);
}
