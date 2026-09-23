package com.EduePoa.EP.academics.curriculum.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.curriculum.entity.CurriculumVersion;
import com.EduePoa.EP.academics.curriculum.entity.PertinentContemporaryIssue;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PertinentContemporaryIssueRepository extends TenantAwareRepository<PertinentContemporaryIssue, Long> {
    List<PertinentContemporaryIssue> findByCurriculumVersionOrderBySequenceAsc(CurriculumVersion curriculumVersion);
    Optional<PertinentContemporaryIssue> findByCurriculumVersionAndCode(CurriculumVersion curriculumVersion, String code);
    boolean existsByCurriculumVersionAndCode(CurriculumVersion curriculumVersion, String code);
}
