package com.EduePoa.EP.academics.assessment.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.assessment.entity.AssessmentType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssessmentTypeRepository extends TenantAwareRepository<AssessmentType, Long> {
    Optional<AssessmentType> findByCode(String code);
    boolean existsByCode(String code);
    List<AssessmentType> findByActiveTrue();
}
