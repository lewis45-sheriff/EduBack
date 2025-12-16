package com.EduePoa.EP.FinanceTransaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinanceTransactionRepository  extends JpaRepository<FinanceTransaction,Long> {
    List<FinanceTransaction> findByStudentId(Long studentId);
    List<FinanceTransaction> findAllByOrderByTransactionDateDesc();

}
