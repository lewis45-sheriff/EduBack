package com.EduePoa.EP.academics.assessment.repository;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.assessment.entity.Assessment;
import com.EduePoa.EP.academics.curriculum.entity.LearningArea;
import org.springframework.stereotype.Repository;

import java.time.Year;
import java.util.List;

@Repository
public interface AssessmentRepository extends TenantAwareRepository<Assessment, Long> {
    List<Assessment> findByGradeIdAndTermAndYear(Long gradeId, Term term, Year year);
    List<Assessment> findByLearningAreaAndTermAndYear(LearningArea learningArea, Term term, Year year);
    List<Assessment> findByGradeIdAndLearningAreaAndTermAndYear(Long gradeId, LearningArea learningArea, Term term, Year year);
}
