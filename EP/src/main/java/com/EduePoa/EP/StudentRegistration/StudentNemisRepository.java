package com.EduePoa.EP.StudentRegistration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentNemisRepository extends JpaRepository<StudentNemis, Long> {
    Optional<StudentNemis> findByStudentId(Long studentId);
}
