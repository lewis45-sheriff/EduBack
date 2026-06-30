package com.EduePoa.EP.Multitenancy.repository;

import com.EduePoa.EP.Multitenancy.entity.TenantAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenantAuditLogRepository extends JpaRepository<TenantAuditLog, Long> {

    List<TenantAuditLog> findByTenantIdentifier(String tenantIdentifier);

    List<TenantAuditLog> findByTenantIdentifierOrderByTimestampDesc(String tenantIdentifier);

    List<TenantAuditLog> findByAction(String action);
}
