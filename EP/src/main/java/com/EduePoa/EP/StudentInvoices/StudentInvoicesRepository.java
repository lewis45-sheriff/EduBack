package com.EduePoa.EP.StudentInvoices;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.StudentRegistration.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Year;
import java.util.List;
import java.util.Optional;

public interface StudentInvoicesRepository extends JpaRepository<StudentInvoices,Long> {
    Optional<StudentInvoices> findByStudentAndTermAndAcademicYear(
            Student student,
            Term term,
            Year academicYear
    );
    List<StudentInvoices> findAllByStudent_IdAndIsDeleted(Long studentId, char isDeleted);
    List<StudentInvoices> findByTerm(Term term);

    // Find invoices by term and academic year
    List<StudentInvoices> findByTermAndAcademicYear(Term term, Year academicYear);

    // Find invoices by term, excluding deleted ones
    List<StudentInvoices> findByTermAndIsDeleted(Term term, char isDeleted);

}
