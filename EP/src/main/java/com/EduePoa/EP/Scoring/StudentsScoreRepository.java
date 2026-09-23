package com.EduePoa.EP.Scoring;


import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.academics.entity.AcademicSubject;
import org.springframework.stereotype.Repository;

import java.time.Year;
import java.util.List;

@Repository
public interface StudentsScoreRepository extends TenantAwareRepository<StudentsScore, Long> {

    List<StudentsScore> findByAcademicSubjectAndTermAndGrade(
            AcademicSubject academicSubject, Term term, Grade grade);

    List<StudentsScore> findByTermAndGrade(Term term, Grade grade);

    boolean existsByStudentAndGradeAndTermAndAcademicSubjectAndExamTypeAndYear(
            Student student, Grade grade, Term term,
           AcademicSubject academicSubject, ExamType examType, Year year);

    // --- CBC engine finders (tenant-filtered automatically) ---

    List<StudentsScore> findByGradeAndTermAndYear(Grade grade, Term term, Year year);

    List<StudentsScore> findByStudentAndTermAndYear(Student student, Term term, Year year);

    List<StudentsScore> findByStudentAndAcademicSubjectAndTermAndYear(
            Student student, AcademicSubject academicSubject, Term term, Year year);
}
