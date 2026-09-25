package com.EduePoa.EP.PaymentTransfer;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentTransferRepository extends TenantAwareRepository<PaymentTransfer, Long> {

    List<PaymentTransfer> findAllByOrderByCreatedAtDesc();

    List<PaymentTransfer> findByStatusOrderByCreatedAtDesc(PaymentTransferStatus status);

    /**
     * Total amount already committed against a payment reference by transfers that
     * are still reserving it (pending) or have consumed it (approved). Rejected
     * transfers release their amount and are excluded. Used to compute how much of
     * a payment is still transferable.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM PaymentTransfer t " +
            "WHERE t.sourcePaymentReference = :reference " +
            "AND t.status IN (com.EduePoa.EP.PaymentTransfer.PaymentTransferStatus.PENDING_APPROVAL, " +
            "com.EduePoa.EP.PaymentTransfer.PaymentTransferStatus.APPROVED)")
    BigDecimal sumCommittedForReference(@Param("reference") String reference);

    /**
     * Same as {@link #sumCommittedForReference(String)} but excluding one transfer
     * id — used at approval time to re-check availability without counting the
     * transfer being approved itself.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM PaymentTransfer t " +
            "WHERE t.sourcePaymentReference = :reference " +
            "AND t.id <> :excludeId " +
            "AND t.status IN (com.EduePoa.EP.PaymentTransfer.PaymentTransferStatus.PENDING_APPROVAL, " +
            "com.EduePoa.EP.PaymentTransfer.PaymentTransferStatus.APPROVED)")
    BigDecimal sumCommittedForReferenceExcluding(@Param("reference") String reference,
                                                 @Param("excludeId") Long excludeId);
}
