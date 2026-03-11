package com.EduePoa.EP.Procurement.Ledger;

import com.EduePoa.EP.Authentication.Enum.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    Page<LedgerEntry> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);
    Page<LedgerEntry> findByTransactionType(TransactionType transactionType, Pageable pageable);
    Page<LedgerEntry> findByTransactionTypeAndTransactionDateBetween(
            TransactionType transactionType, LocalDate startDate, LocalDate endDate, Pageable pageable);
    Page<LedgerEntry> findByReferenceType(String referenceType, Pageable pageable);
    Optional<LedgerEntry> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l " +
            "WHERE l.transactionType = 'CREDIT' " +
            "AND l.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumCreditsByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l " +
            "WHERE l.transactionType = 'DEBIT' " +
            "AND l.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumDebitsByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    Optional<LedgerEntry> findTopByOrderByIdDesc();

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l " +
            "WHERE l.transactionType = 'CREDIT'")
    BigDecimal sumAllCredits();

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l " +
            "WHERE l.transactionType = 'DEBIT'")
    BigDecimal sumAllDebits();

    // Reports: group by referenceType for money-in/out breakdown
    @Query("SELECT l.referenceType, COALESCE(SUM(l.amount), 0), COUNT(l) FROM LedgerEntry l " +
            "WHERE l.transactionType = :type " +
            "AND l.transactionDate BETWEEN :startDate AND :endDate GROUP BY l.referenceType")
    List<Object[]> sumByReferenceTypeAndDateRange(
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT MONTH(l.transactionDate), l.transactionType, COALESCE(SUM(l.amount), 0) " +
            "FROM LedgerEntry l WHERE YEAR(l.transactionDate) = :year " +
            "GROUP BY MONTH(l.transactionDate), l.transactionType " +
            "ORDER BY MONTH(l.transactionDate)")
    List<Object[]> monthlyTrend(@Param("year") int year);

    List<LedgerEntry> findByTransactionDateBetweenOrderByTransactionDateAscIdAsc(
            LocalDate startDate, LocalDate endDate);
}
