package com.EduePoa.EP.Scoring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExamTypeGradingRepository extends JpaRepository<ExamTypeGrading, Long> {
}
