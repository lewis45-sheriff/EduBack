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
}
