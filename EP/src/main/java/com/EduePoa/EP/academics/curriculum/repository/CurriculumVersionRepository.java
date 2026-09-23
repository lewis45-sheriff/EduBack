package com.EduePoa.EP.academics.curriculum.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.curriculum.entity.CurriculumVersion;
import com.EduePoa.EP.academics.curriculum.enums.CurriculumStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurriculumVersionRepository extends TenantAwareRepository<CurriculumVersion, Long> {
    Optional<CurriculumVersion> findByCode(String code);
    boolean existsByCode(String code);
    List<CurriculumVersion> findByStatus(CurriculumStatus status);
}
