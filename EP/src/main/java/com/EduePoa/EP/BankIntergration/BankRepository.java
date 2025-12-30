package com.EduePoa.EP.BankIntergration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankRepository extends JpaRepository<Bank, Long > {
    Boolean existsByTransId(String transId);
    Optional<Bank> findByTransId(String transId);

    // Find transactions by student ID
    List<Bank> findByStudentId(Long studentId);

    // Find transactions by bill reference number (admission number)
    List<Bank> findByBillRefNumber(String billRefNumber);

    // Find transactions by date range
    @Query("SELECT b FROM Bank b WHERE b.transTime BETWEEN :startDate AND :endDate ORDER BY b.transTime DESC")
    List<Bank> findByTransTimeBetween(@Param("startDate") String startDate, @Param("endDate") String endDate);

    // Find unmatched transactions (no student linked)
    List<Bank> findByStudentIsNull();

    // Count unmatched transactions
    Long countByStudentIsNull();

    // Calculate total transaction amount
    @Query("SELECT SUM(CAST(b.transAmount AS double)) FROM Bank b")
    Double sumTransAmount();

    // Calculate total unmatched transaction amount
    @Query("SELECT SUM(CAST(b.transAmount AS double)) FROM Bank b WHERE b.student IS NULL")
    Double sumUnmatchedTransAmount();

    // Search by customer name or mobile number
    @Query("SELECT b FROM Bank b WHERE LOWER(b.customerName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR b.mobile LIKE CONCAT('%', :searchTerm, '%') ORDER BY b.id DESC")
    List<Bank> searchByCustomerNameOrMobile(@Param("searchTerm") String search);
}
