package com.EduePoa.EP.Expenses;

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
public interface ExpensesRepository extends JpaRepository<Expenses, Long> {

    boolean existsByExpenseNumber(String expenseNumber);

    Page<Expenses> findAll(Pageable pageable);

    Page<Expenses> findByCategory(String category, Pageable pageable);

    Page<Expenses> findByStatus(String status, Pageable pageable);

    Page<Expenses> findByPaymentMethod(String paymentMethod, Pageable pageable);

    Page<Expenses> findByExpenseDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    @Query("SELECT e FROM Expenses e WHERE " +
           "(:search IS NULL OR LOWER(e.description) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(e.vendorName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(e.category) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:category IS NULL OR e.category = :category) " +
           "AND (:status IS NULL OR e.status = :status) " +
           "AND (:paymentMethod IS NULL OR e.paymentMethod = :paymentMethod) " +
           "AND (:startDate IS NULL OR e.expenseDate >= :startDate) " +
           "AND (:endDate IS NULL OR e.expenseDate <= :endDate)")
    Page<Expenses> findByFilters(
            @Param("search") String search,
            @Param("category") String category,
            @Param("status") String status,
            @Param("paymentMethod") String paymentMethod,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expenses e WHERE e.expenseDate BETWEEN :startDate AND :endDate")
    BigDecimal sumAmountByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expenses e WHERE e.status = :status AND e.expenseDate BETWEEN :startDate AND :endDate")
    BigDecimal sumAmountByStatusAndDateRange(@Param("status") String status, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(e) FROM Expenses e WHERE e.expenseDate BETWEEN :startDate AND :endDate")
    Long countByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Auto-generate next expense number
    @Query("SELECT COUNT(e) FROM Expenses e")
    Long countAll();
}
