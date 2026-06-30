package com.EduePoa.EP.MPesa;


//import com.SFM.SchoolFeeManagement.Payments.FeePayments;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MpesaTransactionRepository extends TenantAwareRepository<MpesaSTKTransactions, Long> {
    Optional<MpesaSTKTransactions> findByMerchantRequestID(String merchantRequestID);
//    Optional<MpesaTransaction> findStudentById(@NonNull Long student_id);
//    Optional<FeePayments> findByMerchantRequestID(String  studentId);
}
