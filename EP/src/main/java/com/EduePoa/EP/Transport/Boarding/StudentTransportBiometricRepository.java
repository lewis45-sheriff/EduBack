package com.EduePoa.EP.Transport.Boarding;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentTransportBiometricRepository
        extends TenantAwareRepository<StudentTransportBiometric, Long> {

    /**
     * Resolve a mapping by its opaque token. Unique within a tenant, so an {@link Optional} is safe.
     */
    Optional<StudentTransportBiometric> findByBiometricId(String biometricId);

    boolean existsByBiometricId(String biometricId);

    /**
     * All tokens enrolled for a student (multiple methods/devices supported).
     */
    List<StudentTransportBiometric> findByStudent(Student student);

    Optional<StudentTransportBiometric> findByStudentAndMethod(Student student, CaptureMethod method);
}
