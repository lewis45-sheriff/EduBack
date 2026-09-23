package com.EduePoa.EP.academics.curriculum.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.curriculum.entity.LearningArea;
import com.EduePoa.EP.academics.curriculum.entity.LearningExperience;
import com.EduePoa.EP.academics.curriculum.entity.SpecificLearningOutcome;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LearningExperienceRepository extends TenantAwareRepository<LearningExperience, Long> {
    List<LearningExperience> findByLearningAreaOrderBySequenceAsc(LearningArea learningArea);
    List<LearningExperience> findByLearningOutcomeOrderBySequenceAsc(SpecificLearningOutcome learningOutcome);
    boolean existsByLearningAreaAndTitle(LearningArea learningArea, String title);
}
