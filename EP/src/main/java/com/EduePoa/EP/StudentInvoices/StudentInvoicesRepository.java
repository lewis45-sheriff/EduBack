package com.EduePoa.EP.StudentInvoices;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.Optional;

public interface StudentInvoicesRepository extends TenantAwareRepository<StudentInvoices, Long> {
    org.springframework.data.domain.Page<StudentInvoices> findByBalanceGreaterThan(BigDecimal balance,
            org.springframework.data.domain.Pageable pageable);

    Optional<StudentInvoices> findByStudentAndTermAndAcademicYear(
            Student student,
            Term term,
            Year academicYear);

    List<StudentInvoices> findAllByStudent_IdAndIsDeleted(Long studentId, char isDeleted);

    // Find all invoices excluding reversed/deleted ones
    List<StudentInvoices> findByIsDeleted(char isDeleted);

    List<StudentInvoices> findByTerm(Term term);

    // Find invoices by term and academic year
    List<StudentInvoices> findByTermAndAcademicYear(Term term, Year academicYear);

    // Find invoices by term, excluding deleted ones
    List<StudentInvoices> findByTermAndIsDeleted(Term term, char isDeleted);

    // Find invoices by term and academic year, excluding deleted ones (school-wide reversal)
    List<StudentInvoices> findByTermAndAcademicYearAndIsDeleted(Term term, Year academicYear, char isDeleted);

    // Find invoices by grade, term and academic year, excluding deleted ones (per-grade reversal)
    List<StudentInvoices> findByStudent_Grade_IdAndTermAndAcademicYearAndIsDeleted(
            Long gradeId, Term term, Year academicYear, char isDeleted);

    @Query("SELECT COALESCE(SUM(si.balance), 0) FROM StudentInvoices si WHERE si.isDeleted = 'N' AND si.status != 'C'")
    BigDecimal sumOutstandingBalance();

    @Query("SELECT COUNT(DISTINCT si.student.id) FROM StudentInvoices si WHERE si.isDeleted = 'N' AND si.status != 'C' AND si.balance > 0")
    Long countStudentsWithOutstanding();

}
