package com.EduePoa.EP.FinanceTransaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface FinanceTransactionRepository  extends JpaRepository<FinanceTransaction,Long> {
    List<FinanceTransaction> findByStudentId(Long studentId);
    List<FinanceTransaction> findAllByOrderByTransactionDateDesc();
    @Query("SELECT FUNCTION('MONTH', t.transactionDate) as month, " +
            "SUM(t.amount) as income " +
            "FROM FinanceTransaction t " +
            "WHERE FUNCTION('YEAR', t.transactionDate) = :year " +
            "AND t.transactionType = 'INCOME' " +
            "GROUP BY FUNCTION('MONTH', t.transactionDate) " +
            "ORDER BY FUNCTION('MONTH', t.transactionDate)")
    List<Object[]> getMonthlyIncomeStatistics(@Param("year") int year);
    @Query("SELECT SUM(t.amount) FROM FinanceTransaction t " +
            "WHERE t.transactionType = :type " +
            "AND FUNCTION('YEAR', t.transactionDate) = :year")
    BigDecimal sumByTransactionTypeAndYear(
            @Param("type") FinanceTransaction.TransactionType type,
            @Param("year") int year
    );

}
