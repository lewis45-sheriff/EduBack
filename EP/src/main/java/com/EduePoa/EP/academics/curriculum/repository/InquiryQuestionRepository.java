package com.EduePoa.EP.academics.curriculum.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.curriculum.entity.InquiryQuestion;
import com.EduePoa.EP.academics.curriculum.entity.SpecificLearningOutcome;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InquiryQuestionRepository extends TenantAwareRepository<InquiryQuestion, Long> {
    List<InquiryQuestion> findByLearningOutcomeOrderBySequenceAsc(SpecificLearningOutcome learningOutcome);
    boolean existsByLearningOutcomeAndQuestion(SpecificLearningOutcome learningOutcome, String question);
}
