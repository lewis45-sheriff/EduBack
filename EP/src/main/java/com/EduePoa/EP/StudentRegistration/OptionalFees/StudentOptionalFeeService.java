package com.EduePoa.EP.StudentRegistration.OptionalFees;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.StudentRegistration.OptionalFees.Request.StudentOptionalFeeAssignRequest;
import com.EduePoa.EP.StudentRegistration.OptionalFees.Request.StudentOptionalFeeBatchAssignRequest;
import com.EduePoa.EP.Utils.CustomResponse;

import java.time.Year;

public interface StudentOptionalFeeService {

    /** Assign an optional catalog item to a student for a term/year. */
    CustomResponse<?> assign(StudentOptionalFeeAssignRequest request);

    /** Assign several optional line items to a student in one call. */
    CustomResponse<?> assignBatch(StudentOptionalFeeBatchAssignRequest request);

    /** Soft-remove an optional fee assignment (subject to invoice-lifecycle rules). */
    CustomResponse<?> remove(Long id);

    /** List optional fee assignments for a student, optionally scoped to term/year. */
    CustomResponse<?> listForStudent(Long studentId, Term term, Year academicYear);
}
