package com.EduePoa.EP.academics.assessment.repository;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.academics.assessment.entity.StudentTermSummary;
import org.springframework.stereotype.Repository;

import java.time.Year;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentTermSummaryRepository extends TenantAwareRepository<StudentTermSummary, Long> {
    Optional<StudentTermSummary> findByStudentAndTermAndYear(Student student, Term term, Year year);
    List<StudentTermSummary> findByGradeIdAndTermAndYear(Long gradeId, Term term, Year year);
    List<StudentTermSummary> findByGradeStreamIdAndTermAndYear(Long gradeStreamId, Term term, Year year);
}
