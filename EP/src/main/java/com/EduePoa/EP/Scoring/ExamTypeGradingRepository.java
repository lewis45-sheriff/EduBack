package com.EduePoa.EP.Scoring;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExamTypeGradingRepository extends TenantAwareRepository<ExamTypeGrading, Long> {
}
