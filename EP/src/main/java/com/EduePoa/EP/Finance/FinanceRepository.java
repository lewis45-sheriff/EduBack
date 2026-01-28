package com.EduePoa.EP.Finance;

import com.EduePoa.EP.Authentication.Enum.Term;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.Optional;
@Repository
public interface FinanceRepository extends JpaRepository<Finance,Long> {
    Optional<Finance> findByStudentId(Long studentId);
    Optional<Finance> findByStudentIdAndTermAndYear(Long studentId, Term term, Year year);
    List<Finance> findByBalanceNot(BigDecimal balance);
    Optional<Finance> findByTermAndYear(Term term, Year year);
    @Query("SELECT f FROM Finance f WHERE f.balance > :minBalance " +
            "AND (f.year < :currentYear OR (f.year = :currentYear AND f.term < :currentTerm)) " +
            "ORDER BY f.balance DESC")
    Page<Finance> findPreviousTermDefaulters(
            @Param("minBalance") BigDecimal minBalance,
            @Param("currentYear") Year currentYear,
            @Param("currentTerm") Term currentTerm,
            Pageable pageable
    );

    // Sum of all outstanding balances from previous terms
    @Query("SELECT COALESCE(SUM(f.balance), 0) FROM Finance f WHERE f.balance > 0 " +
            "AND (f.year < :currentYear OR (f.year = :currentYear AND f.term < :currentTerm))")
    BigDecimal sumPreviousTermOutstandingBalance(
            @Param("currentYear") Year currentYear,
            @Param("currentTerm") Term currentTerm
    );

}
