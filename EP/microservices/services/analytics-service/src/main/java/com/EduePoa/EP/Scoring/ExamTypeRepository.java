package com.EduePoa.EP.Scoring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExamTypeRepository extends JpaRepository<ExamType, Long> {
    boolean existsByName(String name);
}
