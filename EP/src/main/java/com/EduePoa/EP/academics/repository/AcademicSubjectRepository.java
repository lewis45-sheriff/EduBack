package com.EduePoa.EP.academics.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.entity.AcademicSubject;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AcademicSubjectRepository extends TenantAwareRepository<AcademicSubject, Long> {

    Optional<AcademicSubject> findBySubjectName(String subjectName);

    boolean existsBySubjectName(String subjectName);
}
