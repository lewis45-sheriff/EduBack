package com.EduePoa.EP.Transport.AssignTransport;

import com.EduePoa.EP.StudentRegistration.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssignTransportRepository extends JpaRepository<AssignTransport,Long> {
    Optional<AssignTransport> findByStudent(Student student);

}
