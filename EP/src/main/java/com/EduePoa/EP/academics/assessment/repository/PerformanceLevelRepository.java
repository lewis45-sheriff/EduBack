package com.EduePoa.EP.academics.assessment.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.assessment.entity.AssessmentFramework;
import com.EduePoa.EP.academics.assessment.entity.PerformanceLevel;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PerformanceLevelRepository extends TenantAwareRepository<PerformanceLevel, Long> {
    List<PerformanceLevel> findByAssessmentFrameworkOrderBySequenceAsc(AssessmentFramework assessmentFramework);
    Optional<PerformanceLevel> findByAssessmentFrameworkAndCode(AssessmentFramework assessmentFramework, String code);
    boolean existsByAssessmentFrameworkAndCode(AssessmentFramework assessmentFramework, String code);
}
