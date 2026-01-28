package com.EduePoa.EP.FinanceTransaction;

import com.EduePoa.EP.Authentication.Enum.Term;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;

public interface FinanceTransactionRepository extends JpaRepository<FinanceTransaction, Long> {
        List<FinanceTransaction> findByTransactionDateBetween(java.time.LocalDate startDate,
                        java.time.LocalDate endDate);

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
                        @Param("year") int year);

        // In FinanceTransactionRepository interface
        List<FinanceTransaction> findByStudentIdAndTransactionTypeOrderByTransactionDateAsc(
                        Long studentId,
                        FinanceTransaction.TransactionType transactionType);

        @Query("SELECT COALESCE(SUM(ft.amount), 0) FROM FinanceTransaction ft WHERE ft.transactionType = :type")
        BigDecimal sumByTransactionType(@Param("type") FinanceTransaction.TransactionType type);
        // @Query("SELECT COALESCE(SUM(ft.amount), 0) FROM FinanceTransaction ft " +
        // "WHERE ft.studentId = :studentId " +
        // "AND ft.transactionType = 'INCOME' " +
        // "AND ft.term = :term " +
        // "AND ft.year = :year")
        // BigDecimal sumAmountByStudentAndTerm(
        // @Param("studentId") Long studentId,
        // @Param("transactionType") FinanceTransaction.TransactionType transactionType,
        // @Param("term") Term term,
        // @Param("year") Year year
        // );
        //
        // @Query("SELECT COALESCE(SUM(ft.amount), 0) FROM FinanceTransaction ft " +
        // "WHERE ft.studentId = :studentId " +
        // "AND ft.transactionType = 'INCOME' " +
        // "AND ft.year < :year " +
        // "AND ft.term < :term " +
        // "ORDER BY ft.year DESC, ft.term DESC")
        // BigDecimal findBalanceForward(
        // @Param("studentId") Long studentId,
        // @Param("year") Year year,
        // @Param("term") Term term
        // );

}
