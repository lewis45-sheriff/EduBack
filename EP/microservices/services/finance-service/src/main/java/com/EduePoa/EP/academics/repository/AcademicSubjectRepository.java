package com.EduePoa.EP.academics.repository;

import com.EduePoa.EP.academics.entity.AcademicSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AcademicSubjectRepository extends JpaRepository<AcademicSubject, Long> {
}
