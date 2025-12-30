package com.EduePoa.EP.StudentRegistration;

import com.EduePoa.EP.StudentRegistration.Response.StudentsPerGradeDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository< Student,Long> {
    boolean existsByAdmissionNumber(String admissionNumber);
//    String findLastAdmissionNumberForYear(String year);
// In StudentRepository
List<Student> findAllByIsDeleted(Boolean isDeleted);
    @Query("""
        SELECT new com.EduePoa.EP.StudentRegistration.Response.StudentsPerGradeDTO(
            g.name,
            COUNT(s.id)
        )
        FROM Student s
        JOIN s.grade g
        WHERE s.isDeleted = false
        GROUP BY g.name
        ORDER BY g.name
    """)
    List<StudentsPerGradeDTO> countStudentsPerGrade();
    Optional<Student> findByAdmissionNumber(String admissionNumber);

}
