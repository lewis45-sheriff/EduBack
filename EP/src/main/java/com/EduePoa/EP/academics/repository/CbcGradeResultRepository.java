package com.EduePoa.EP.academics.repository;


import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.academics.entity.AcademicSubject;
import com.EduePoa.EP.academics.entity.CbcGradeResult;
import org.springframework.stereotype.Repository;

import java.time.Year;
import java.util.List;
import java.util.Optional;

@Repository
public interface CbcGradeResultRepository extends TenantAwareRepository<CbcGradeResult, Long> {

    List<CbcGradeResult> findByStudentAndTerm(Student student, Term term);

    List<CbcGradeResult> findByStudentAndTermAndYear(Student student, Term term, Year year);

    Optional<CbcGradeResult> findByStudentAndAcademicSubjectAndTermAndYear(
            Student student, AcademicSubject subject, Term term, Year year);
}
