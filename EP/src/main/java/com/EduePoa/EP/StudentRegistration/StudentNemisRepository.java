package com.EduePoa.EP.StudentRegistration;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentNemisRepository extends TenantAwareRepository<StudentNemis, Long> {
    Optional<StudentNemis> findByStudentId(Long studentId);
}
