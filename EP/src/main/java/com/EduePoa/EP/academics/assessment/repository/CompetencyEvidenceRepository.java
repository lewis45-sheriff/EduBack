package com.EduePoa.EP.academics.assessment.repository;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.academics.assessment.entity.CompetencyEvidence;
import org.springframework.stereotype.Repository;

import java.time.Year;
import java.util.List;

@Repository
public interface CompetencyEvidenceRepository extends TenantAwareRepository<CompetencyEvidence, Long> {
    List<CompetencyEvidence> findByStudentAndTermAndAcademicYear(Student student, Term term, Year academicYear);
    List<CompetencyEvidence> findByStudent(Student student);
}
