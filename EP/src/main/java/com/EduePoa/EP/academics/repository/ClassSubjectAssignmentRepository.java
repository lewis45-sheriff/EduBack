package com.EduePoa.EP.academics.repository;


import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.entity.AcademicSubject;
import com.EduePoa.EP.academics.entity.ClassSubjectAssignment;
import org.springframework.stereotype.Repository;

import java.time.Year;
import java.util.List;

@Repository
public interface ClassSubjectAssignmentRepository extends TenantAwareRepository<ClassSubjectAssignment, Long> {

    List<ClassSubjectAssignment> findByGradeAndYear(Grade grade, Year year);

    boolean existsByGradeAndAcademicSubjectAndYear(Grade grade, AcademicSubject subject, Year year);
}
