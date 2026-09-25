package com.EduePoa.EP.StudentInvoices;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;

import java.time.Year;
import java.util.List;

public interface InvoiceReversalRepository extends TenantAwareRepository<InvoiceReversal, Long> {

    List<InvoiceReversal> findAllByOrderByReversedAtDesc();

    List<InvoiceReversal> findByStudentIdOrderByReversedAtDesc(Long studentId);

    List<InvoiceReversal> findByTermAndAcademicYearOrderByReversedAtDesc(Term term, Year academicYear);
}
