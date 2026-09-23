package com.EduePoa.EP.academics.curriculum.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.curriculum.entity.CurriculumVersion;
import com.EduePoa.EP.academics.curriculum.entity.EducationLevel;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EducationLevelRepository extends TenantAwareRepository<EducationLevel, Long> {
    List<EducationLevel> findByCurriculumVersionOrderBySequenceAsc(CurriculumVersion curriculumVersion);
    Optional<EducationLevel> findByCurriculumVersionAndCode(CurriculumVersion curriculumVersion, String code);
    boolean existsByCurriculumVersionAndCode(CurriculumVersion curriculumVersion, String code);
}
