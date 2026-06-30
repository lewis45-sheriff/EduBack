package com.EduePoa.EP.Authentication.Role;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends TenantAwareRepository<Role, Long> {
    Optional<Role> findByName(String roleName);

}
