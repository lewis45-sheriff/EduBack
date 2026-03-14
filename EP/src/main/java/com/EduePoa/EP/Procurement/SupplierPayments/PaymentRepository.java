package com.EduePoa.EP.Procurement.SupplierPayments;

import com.EduePoa.EP.Authentication.Enum.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findBySupplierId(Long supplierId, Pageable pageable);

    Page<Payment> findBySupplierInvoiceId(Long invoiceId, Pageable pageable);

    // Sum of all payments in a given status for an invoice (e.g. PENDING_APPROVAL)
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.supplierInvoice.id = :invoiceId AND p.status = :status")
    BigDecimal sumAmountByInvoiceIdAndStatus(@Param("invoiceId") Long invoiceId, @Param("status") PaymentStatus status);

    // Reports: date-range filtered supplier payments
    List<Payment> findByPaymentDateBetween(LocalDate startDate, LocalDate endDate);
}
