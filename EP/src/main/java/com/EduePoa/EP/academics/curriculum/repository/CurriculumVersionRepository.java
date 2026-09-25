package com.EduePoa.EP.academics.curriculum.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.curriculum.entity.CurriculumVersion;
import com.EduePoa.EP.academics.curriculum.enums.CurriculumStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurriculumVersionRepository extends TenantAwareRepository<CurriculumVersion, Long> {
    Optional<CurriculumVersion> findByCode(String code);
    boolean existsByCode(String code);
    List<CurriculumVersion> findByStatus(CurriculumStatus status);

    /**
     * Tenant-explicit lookup. Seeders run outside an HTTP request where the Hibernate tenantFilter
     * is not reliably active across repository transactions, so scope by tenant_id directly to avoid
     * matching another tenant's version.
     */
    @org.springframework.data.jpa.repository.Query(
            "select v from CurriculumVersion v where v.code = :code and v.tenantId = :tenantId")
    Optional<CurriculumVersion> findByCodeAndTenant(
            @org.springframework.data.repository.query.Param("code") String code,
            @org.springframework.data.repository.query.Param("tenantId") String tenantId);
}
