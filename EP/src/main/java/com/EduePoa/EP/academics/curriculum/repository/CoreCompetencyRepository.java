package com.EduePoa.EP.academics.curriculum.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.curriculum.entity.CoreCompetency;
import com.EduePoa.EP.academics.curriculum.entity.CurriculumVersion;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoreCompetencyRepository extends TenantAwareRepository<CoreCompetency, Long> {
    List<CoreCompetency> findByCurriculumVersionOrderBySequenceAsc(CurriculumVersion curriculumVersion);
    Optional<CoreCompetency> findByCurriculumVersionAndCode(CurriculumVersion curriculumVersion, String code);
    boolean existsByCurriculumVersionAndCode(CurriculumVersion curriculumVersion, String code);
}
