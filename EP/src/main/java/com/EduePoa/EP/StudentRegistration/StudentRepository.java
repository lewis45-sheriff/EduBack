package com.EduePoa.EP.StudentRegistration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentRepository extends JpaRepository< Student,Long> {
    boolean existsByAdmissionNumber(String admissionNumber);
//    String findLastAdmissionNumberForYear(String year);
// In StudentRepository
List<Student> findAllByIsDeleted(Boolean isDeleted);
}
