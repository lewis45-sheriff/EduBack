package com.EduePoa.EP.TransactionReversal;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;

import java.util.List;

public interface TransactionReversalRepository extends TenantAwareRepository<TransactionReversal, Long> {

    List<TransactionReversal> findAllByOrderByCreatedAtDesc();

    List<TransactionReversal> findByStatusOrderByCreatedAtDesc(TransactionReversalStatus status);

    /**
     * True when a reversal for the given transaction is already pending or approved,
     * so the same transaction cannot be reversed twice.
     */
    boolean existsByFinanceTransactionIdAndStatusIn(Long financeTransactionId,
                                                    List<TransactionReversalStatus> statuses);
}
