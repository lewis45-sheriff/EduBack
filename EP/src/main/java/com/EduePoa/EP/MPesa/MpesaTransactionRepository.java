package com.EduePoa.EP.MPesa;


//import com.SFM.SchoolFeeManagement.Payments.FeePayments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MpesaTransactionRepository extends JpaRepository<MpesaSTKTransactions, Long> {
//    Optional<MpesaTransaction> findStudentById(@NonNull Long student_id);
//    Optional<FeePayments> findByMerchantRequestID(String  studentId);
}
