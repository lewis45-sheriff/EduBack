package com.EduePoa.EP.Staff;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffRepository extends TenantAwareRepository<Staff, Long> {
    Optional<Staff> findByEmail(String email);
    Optional<Staff> findByPhoneNumber(String phoneNumber);
    Optional<Staff> findByEmployeeNumber(String employeeNumber);
    Optional<Staff> findByUserId(Long userId);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByEmployeeNumber(String employeeNumber);
}
