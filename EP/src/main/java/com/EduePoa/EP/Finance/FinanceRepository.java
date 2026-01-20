package com.EduePoa.EP.Finance;

import com.EduePoa.EP.Authentication.Enum.Term;
import org.springframework.data.jpa.repository.JpaRepository;
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

}
