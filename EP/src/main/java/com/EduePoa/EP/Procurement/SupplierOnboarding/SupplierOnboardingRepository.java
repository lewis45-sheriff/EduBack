package com.EduePoa.EP.Procurement.SupplierOnboarding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierOnboardingRepository extends JpaRepository<SupplierOnboarding, Long> {
    Optional<SupplierOnboarding> findByUser_Id(Long userId);

    Optional<SupplierOnboarding> findByBusinessRegistrationNumber(String registrationNumber);

    Optional<SupplierOnboarding> findByBusinessEmail(String businessEmail);

    boolean existsByBusinessRegistrationNumber(String registrationNumber);

    boolean existsByBusinessEmail(String businessEmail);
}
