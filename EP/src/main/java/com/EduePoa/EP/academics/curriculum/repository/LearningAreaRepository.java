package com.EduePoa.EP.academics.curriculum.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.curriculum.entity.CurriculumVersion;
import com.EduePoa.EP.academics.curriculum.entity.EducationLevel;
import com.EduePoa.EP.academics.curriculum.entity.LearningArea;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LearningAreaRepository extends TenantAwareRepository<LearningArea, Long> {
    List<LearningArea> findByCurriculumVersionOrderBySequenceAsc(CurriculumVersion curriculumVersion);
    List<LearningArea> findByEducationLevelOrderBySequenceAsc(EducationLevel educationLevel);
    Optional<LearningArea> findByCurriculumVersionAndCatalogueKey(CurriculumVersion curriculumVersion, String catalogueKey);
    boolean existsByCurriculumVersionAndCatalogueKey(CurriculumVersion curriculumVersion, String catalogueKey);
}
