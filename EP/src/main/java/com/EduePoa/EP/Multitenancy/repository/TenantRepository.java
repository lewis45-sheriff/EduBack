package com.EduePoa.EP.Multitenancy.repository;

import com.EduePoa.EP.Multitenancy.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByTenantIdentifier(String tenantIdentifier);

    boolean existsByTenantIdentifier(String tenantIdentifier);
}
