package com.EduePoa.EP.FinanceTransaction.PendingTransaction;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;

import java.util.List;

public interface PendingTransactionRepository extends TenantAwareRepository<PendingTransaction, Long> {

    List<PendingTransaction> findAllByOrderByCreatedAtDesc();

    List<PendingTransaction> findByStatusOrderByCreatedAtDesc(PendingTransactionStatus status);
}
