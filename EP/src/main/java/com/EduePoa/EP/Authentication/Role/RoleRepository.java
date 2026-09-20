package com.EduePoa.EP.Authentication.Role;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends TenantAwareRepository<Role, Long> {
    Optional<Role> findByName(String roleName);

    /**
     * Tenant-scoped role lookup. Unlike {@link #findByName(String)}, this
     * explicitly constrains by tenant_id so it stays correct even when the
     * Hibernate tenant filter is not active (e.g. during tenant provisioning
     * performed by a Platform_Admin, where the filter is bypassed).
     */
    Optional<Role> findByNameAndTenantId(String roleName, String tenantId);

}
