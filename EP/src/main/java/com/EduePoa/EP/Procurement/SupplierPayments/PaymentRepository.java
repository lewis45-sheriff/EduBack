package com.EduePoa.EP.Procurement.SupplierPayments;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findBySupplierId(Long supplierId, Pageable pageable);

    Page<Payment> findBySupplierInvoiceId(Long invoiceId, Pageable pageable);

    // Reports: date-range filtered supplier payments
    List<Payment> findByPaymentDateBetween(LocalDate startDate, LocalDate endDate);
}
