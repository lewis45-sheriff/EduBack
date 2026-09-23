package com.EduePoa.EP.academics.assessment.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.assessment.entity.AssessmentFramework;
import com.EduePoa.EP.academics.curriculum.entity.CurriculumVersion;
import com.EduePoa.EP.academics.curriculum.enums.CbcEducationLevel;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssessmentFrameworkRepository extends TenantAwareRepository<AssessmentFramework, Long> {
    Optional<AssessmentFramework> findByCurriculumVersionAndCode(CurriculumVersion curriculumVersion, String code);
    boolean existsByCurriculumVersionAndCode(CurriculumVersion curriculumVersion, String code);
    List<AssessmentFramework> findByCurriculumVersion(CurriculumVersion curriculumVersion);
    List<AssessmentFramework> findByCurriculumVersionAndEducationLevel(CurriculumVersion curriculumVersion, CbcEducationLevel educationLevel);
}
