package com.EduePoa.EP.academics.repository;

import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.academics.entity.Attendance;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends TenantAwareRepository<Attendance, Long> {

    Optional<Attendance> findByStudentAndDate(Student student, LocalDate date);

    List<Attendance> findByDate(LocalDate date);

    List<Attendance> findByGradeAndDate(Grade grade, LocalDate date);

    List<Attendance> findByGrade(Grade grade);

    List<Attendance> findByGradeAndDateBetween(Grade grade, LocalDate from, LocalDate to);

    List<Attendance> findByDateBetween(LocalDate from, LocalDate to);
}
