package com.EduePoa.EP.academics.curriculum.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.curriculum.entity.CurriculumVersion;
import com.EduePoa.EP.academics.curriculum.entity.EducationLevel;
import com.EduePoa.EP.academics.curriculum.entity.GradeLevelMapping;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GradeLevelMappingRepository extends TenantAwareRepository<GradeLevelMapping, Long> {
    List<GradeLevelMapping> findByCurriculumVersionOrderBySequenceAsc(CurriculumVersion curriculumVersion);
    List<GradeLevelMapping> findByEducationLevel(EducationLevel educationLevel);
    Optional<GradeLevelMapping> findByCurriculumVersionAndGradeCode(CurriculumVersion curriculumVersion, String gradeCode);
    Optional<GradeLevelMapping> findByCurriculumVersionAndGradeId(CurriculumVersion curriculumVersion, Long gradeId);
    boolean existsByCurriculumVersionAndGradeCode(CurriculumVersion curriculumVersion, String gradeCode);
}
