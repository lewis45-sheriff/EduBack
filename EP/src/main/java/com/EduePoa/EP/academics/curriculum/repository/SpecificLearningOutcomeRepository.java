package com.EduePoa.EP.academics.curriculum.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.curriculum.entity.SpecificLearningOutcome;
import com.EduePoa.EP.academics.curriculum.entity.SubStrand;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpecificLearningOutcomeRepository extends TenantAwareRepository<SpecificLearningOutcome, Long> {
    List<SpecificLearningOutcome> findBySubStrandOrderBySequenceAsc(SubStrand subStrand);
    Optional<SpecificLearningOutcome> findBySubStrandAndCode(SubStrand subStrand, String code);
    boolean existsBySubStrandAndCode(SubStrand subStrand, String code);
}
