package com.EduePoa.EP.academics.curriculum.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.curriculum.entity.CbcValue;
import com.EduePoa.EP.academics.curriculum.entity.CurriculumVersion;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CbcValueRepository extends TenantAwareRepository<CbcValue, Long> {
    List<CbcValue> findByCurriculumVersionOrderBySequenceAsc(CurriculumVersion curriculumVersion);
    Optional<CbcValue> findByCurriculumVersionAndCode(CurriculumVersion curriculumVersion, String code);
    boolean existsByCurriculumVersionAndCode(CurriculumVersion curriculumVersion, String code);
}
