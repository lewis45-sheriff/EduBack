package com.EduePoa.EP.academics.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.entity.AcademicSubject;
import org.springframework.stereotype.Repository;

@Repository
public interface AcademicSubjectRepository extends TenantAwareRepository<AcademicSubject, Long> {
}
