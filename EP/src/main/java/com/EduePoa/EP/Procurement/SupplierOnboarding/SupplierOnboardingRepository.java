package com.EduePoa.EP.Procurement.SupplierOnboarding;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierOnboardingRepository extends TenantAwareRepository<SupplierOnboarding, Long> {
    Optional<SupplierOnboarding> findByUser_Id(Long userId);

    Optional<SupplierOnboarding> findByBusinessRegistrationNumber(String registrationNumber);

    Optional<SupplierOnboarding> findByBusinessEmail(String businessEmail);

    boolean existsByBusinessRegistrationNumber(String registrationNumber);

    boolean existsByBusinessEmail(String businessEmail);
}
