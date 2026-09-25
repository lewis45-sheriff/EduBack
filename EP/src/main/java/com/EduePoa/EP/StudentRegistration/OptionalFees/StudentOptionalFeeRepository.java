package com.EduePoa.EP.StudentRegistration.OptionalFees;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.time.Year;
import java.util.List;
import java.util.Optional;

/**
 * Tenant-aware repository for {@link StudentOptionalFee}. All queries inherit
 * the tenant filter from {@link TenantAwareRepository} and never expose
 * soft-deleted assignments as active.
 */
@Repository
public interface StudentOptionalFeeRepository extends TenantAwareRepository<StudentOptionalFee, Long> {

    /** Active optional fees for a student in a given term and academic year. */
    List<StudentOptionalFee> findByStudent_IdAndTermAndAcademicYearAndIsDeleted(
            Long studentId, Term term, Year academicYear, char isDeleted);

    /** All active optional fees for a student, regardless of term/year. */
    List<StudentOptionalFee> findByStudent_IdAndIsDeleted(Long studentId, char isDeleted);

    /** Duplicate-protection existence check for an active assignment. */
    boolean existsByStudent_IdAndFeeComponentConfig_IdAndTermAndAcademicYearAndIsDeleted(
            Long studentId, Integer feeComponentConfigId, Term term, Year academicYear, char isDeleted);

    /** Fetch the active duplicate assignment, if any. */
    Optional<StudentOptionalFee> findByStudent_IdAndFeeComponentConfig_IdAndTermAndAcademicYearAndIsDeleted(
            Long studentId, Integer feeComponentConfigId, Term term, Year academicYear, char isDeleted);

    /** Find an assignment by id constrained to the active soft-delete state. */
    Optional<StudentOptionalFee> findByIdAndIsDeleted(Long id, char isDeleted);
}
