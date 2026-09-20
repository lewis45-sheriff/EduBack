package com.EduePoa.EP.FeeStructure;

import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;

import java.time.Year;
import java.util.List;
import java.util.Optional;

public interface FeeStructureRepository extends TenantAwareRepository<FeeStructure, Long> {
    Optional<FeeStructure> findByGradeAndYear(Grade grade, Integer year);
     List<FeeStructure> findByIsDeletedAndDeletedOrderByDatePostedDesc(char isDeleted, char deleted);
    Optional<FeeStructure> findByGrade(Grade grade);

    // Mode-aware lookups: a grade has at most one structure per mode (per year).
    Optional<FeeStructure> findByGradeAndModeAndYear(Grade grade, FeeMode mode, Integer year);
    Optional<FeeStructure> findByGradeAndMode(Grade grade, FeeMode mode);
    List<FeeStructure> findByGradeAndModeAndYearAndIsDeletedAndDeleted(
            Grade grade, FeeMode mode, Integer year, char isDeleted, char deleted);

}
