package com.EduePoa.EP.StudentRegistration;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentGuardianRepository extends TenantAwareRepository<StudentGuardian, Long> {
    List<StudentGuardian> findByStudentId(Long studentId);
}
