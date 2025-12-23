package com.EduePoa.EP.Finance;

import com.EduePoa.EP.Authentication.Enum.Term;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Year;
import java.util.Optional;

public interface FinanceRepository extends JpaRepository<Finance,Long> {
    Optional<Finance> findByStudentId(Long studentId);
    Optional<Finance> findByStudentIdAndTermAndYear(Long studentId, Term term, Year year);
}
