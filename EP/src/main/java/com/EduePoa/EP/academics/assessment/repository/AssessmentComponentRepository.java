package com.EduePoa.EP.academics.assessment.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.assessment.entity.AssessmentComponent;
import com.EduePoa.EP.academics.assessment.entity.AssessmentFramework;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssessmentComponentRepository extends TenantAwareRepository<AssessmentComponent, Long> {
    List<AssessmentComponent> findByAssessmentFrameworkOrderBySequenceAsc(AssessmentFramework assessmentFramework);
    boolean existsByAssessmentFrameworkAndCode(AssessmentFramework assessmentFramework, String code);
}
