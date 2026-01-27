package com.EduePoa.EP.Transport.AssignTransport;

import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.Transport.Transport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssignTransportRepository extends JpaRepository<AssignTransport,Long> {
    Optional<AssignTransport> findByStudent(Student student);
    long countByVehicle(Transport vehicle);
    List<AssignTransport> findByVehicle(Transport vehicle);

}
