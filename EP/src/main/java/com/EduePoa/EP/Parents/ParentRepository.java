package com.EduePoa.EP.Parents;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParentRepository extends TenantAwareRepository<Parent, Long> {
    Optional<Parent> findByEmail(String email);
    Optional<Parent> findByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
}
