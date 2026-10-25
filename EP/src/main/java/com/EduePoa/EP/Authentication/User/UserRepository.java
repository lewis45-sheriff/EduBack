package com.EduePoa.EP.Authentication.User;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends TenantAwareRepository<User, Long> {
   Optional< User> findByUsername(String usename);
    Optional<User> findByEmail(String email);

    /**
     * Tenant-disambiguated lookup for Google SSO. Because email is unique only per
     * tenant ({@code (email, tenant_id)}), a Google sign-in that supplies a tenant
     * hint resolves the exact user with this finder. Uses a native query that
     * bypasses the Hibernate tenant filter so SSO (which runs before any tenant
     * context is established) can locate the user across the shared schema.
     */
    @Query(value = "SELECT u.* FROM user u WHERE u.email = :email AND u.tenant_id = :tenantId",
            nativeQuery = true)
    Optional<User> findByEmailAndTenantId(@Param("email") String email,
                                          @Param("tenantId") String tenantId);

    /**
     * All user rows matching an email across every tenant. SSO uses this to detect
     * whether a Google email is ambiguous (present in more than one tenant) when no
     * tenant hint is supplied. Native query to ignore the per-request tenant filter.
     */
    @Query(value = "SELECT u.* FROM user u WHERE u.email = :email", nativeQuery = true)
    List<User> findAllByEmailAcrossTenants(@Param("email") String email);
    @Query(nativeQuery = true, value = "Select count(*) from user join role r on r.id = user.role_id where r.name=:roleName")
    Integer adminCount(@Param("roleName") String roleName);
    Optional<User>findByUsernameIgnoreCaseOrEmailIgnoreCase(String userName,String email);
    boolean existsByEmail(String email);
    @Query(value = "SELECT u.* " +
            "FROM user u " +
            "JOIN role r ON u.role_id = r.id " +
            "WHERE r.name = 'ROLE_PARENT'", nativeQuery = true)
    List<User> findParents();



}
