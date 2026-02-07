package com.EduePoa.EP.Authentication.AuditLogs;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditRepository extends JpaRepository<Audit, UUID> {

    /**
     * Find all audit logs ordered by timestamp descending with pagination
     */
    Page<Audit> findAllByOrderByTimestampDesc(Pageable pageable);

    /**
     * Find audit logs by specific date
     */
    @Query(value = "SELECT * FROM audit WHERE DATE(timestamp) = :date ORDER BY timestamp DESC", nativeQuery = true)
    List<Audit> findByDate(@Param("date") Date date);

    /**
     * Find audit logs within a date range
     */
    @Query(value = "SELECT * FROM audit WHERE DATE(timestamp) BETWEEN :startDate AND :endDate ORDER BY timestamp DESC", nativeQuery = true)
    List<Audit> findByDateRange(@Param("startDate") Date startDate, @Param("endDate") Date endDate);

    /**
     * Find audit logs by module
     */
    List<Audit> findByModuleIgnoreCaseOrderByTimestampDesc(String module);

    /**
     * Find audit logs by user email
     */
    List<Audit> findByUserEmailIgnoreCaseOrderByTimestampDesc(String userEmail);

    /**
     * Search audit logs by activity keyword
     */
    @Query("SELECT a FROM Audit a WHERE LOWER(a.activity) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY a.timestamp DESC")
    List<Audit> searchByActivity(@Param("keyword") String keyword);

    /**
     * Find audit logs by module and date range
     */
    @Query(value = "SELECT * FROM audit WHERE module = :module AND DATE(timestamp) BETWEEN :startDate AND :endDate ORDER BY timestamp DESC", nativeQuery = true)
    List<Audit> findByModuleAndDateRange(@Param("module") String module, @Param("startDate") Date startDate,
            @Param("endDate") Date endDate);
}
