package com.EduePoa.EP.Finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinanceRepository extends JpaRepository<Finance,Long> {
    Optional<Finance> findByStudentId(Long studentId);
}
