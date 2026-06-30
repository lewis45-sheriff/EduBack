package com.EduePoa.EP.Transport.AssignTransport;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.Transport.Transport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssignTransportRepository extends TenantAwareRepository<AssignTransport, Long> {
    Optional<AssignTransport> findByStudent(Student student);
    long countByVehicle(Transport vehicle);
    List<AssignTransport> findByVehicle(Transport vehicle);

}
