package com.EduePoa.EP.Scoring;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExamTypeRepository extends TenantAwareRepository<ExamType, Long> {
    boolean existsByName(String name);
}
