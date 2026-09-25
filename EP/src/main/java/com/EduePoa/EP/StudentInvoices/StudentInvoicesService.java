package com.EduePoa.EP.StudentInvoices;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Utils.CustomResponse;

public interface StudentInvoicesService {
    CustomResponse<?>create(Long studentId,String term);
    CustomResponse<?>invoiceAll(String term);
    CustomResponse<?> getAllInvoices();
    CustomResponse<?>getAllInvoices(Long id);
    CustomResponse<?>getCurrentTermInvoices();
    CustomResponse<?> getInvoicesByTerm(Term term);

    /** Reverse (void) a single invoice by id, undoing its finance and optional-fee effects. */
    CustomResponse<?> reverseInvoice(Long invoiceId);

    /** Reverse (void) a student's invoice for a specific term/academic year. */
    CustomResponse<?> reverseInvoice(Long studentId, Term term, Integer academicYear);

    /** Reverse (void) every invoice school-wide for a term/academic year. */
    CustomResponse<?> reverseAll(Term term, Integer academicYear);

    /** Reverse (void) every invoice for a specific grade for a term/academic year. */
    CustomResponse<?> reverseByGrade(Long gradeId, Term term, Integer academicYear);

    /** List saved invoice-reversal operations (reversal history) for the frontend. */
    CustomResponse<?> getReversals();
}
