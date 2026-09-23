package com.EduePoa.EP.academics.curriculum.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.curriculum.entity.LearningArea;
import com.EduePoa.EP.academics.curriculum.entity.Strand;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StrandRepository extends TenantAwareRepository<Strand, Long> {
    List<Strand> findByLearningAreaOrderBySequenceAsc(LearningArea learningArea);
    Optional<Strand> findByLearningAreaAndCode(LearningArea learningArea, String code);
    boolean existsByLearningAreaAndCode(LearningArea learningArea, String code);
}
