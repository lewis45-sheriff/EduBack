package com.EduePoa.EP.StudentRegistration;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository< Student,Long> {
    boolean existsByAdmissionNumber(String admissionNumber);
//    String findLastAdmissionNumberForYear(String year);
}
