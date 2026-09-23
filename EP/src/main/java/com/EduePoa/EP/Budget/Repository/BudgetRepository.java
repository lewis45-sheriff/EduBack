package com.EduePoa.EP.Budget.Repository;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Budget.Entity.Budget;
import com.EduePoa.EP.Budget.Enum.BudgetStatus;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;

@Repository
public interface BudgetRepository extends TenantAwareRepository<Budget, Long> {

    @Query("SELECT b FROM Budget b WHERE " +
            "(:status IS NULL OR b.status = :status) " +
            "AND (:academicYear IS NULL OR b.academicYear = :academicYear) " +
            "AND (:academicTerm IS NULL OR b.academicTerm = :academicTerm) " +
            "AND (:startDate IS NULL OR b.endDate >= :startDate) " +
            "AND (:endDate IS NULL OR b.startDate <= :endDate) " +
            "AND (:search IS NULL OR LOWER(b.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Budget> findByFilters(@Param("status") BudgetStatus status,
                               @Param("academicYear") Year academicYear,
                               @Param("academicTerm") Term academicTerm,
                               @Param("startDate") LocalDate startDate,
                               @Param("endDate") LocalDate endDate,
                               @Param("search") String search,
                               Pageable pageable);

    /**
     * Approved budgets whose window covers the given date. Used by the
     * non-blocking expense-approval budget warning (Phase 3).
     */
    @Query("SELECT b FROM Budget b WHERE b.status = :status " +
            "AND b.startDate <= :date AND b.endDate >= :date")
    List<Budget> findByStatusAndDateWithinWindow(@Param("status") BudgetStatus status,
                                                 @Param("date") LocalDate date);
}
