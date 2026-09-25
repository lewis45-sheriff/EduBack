package com.EduePoa.EP.academics.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.entity.AcademicSubject;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AcademicSubjectRepository extends TenantAwareRepository<AcademicSubject, Long> {

    Optional<AcademicSubject> findBySubjectName(String subjectName);

    boolean existsBySubjectName(String subjectName);

    /**
     * Tenant-explicit lookup by name. Startup seeders run outside an HTTP request, so the
     * request-scoped Hibernate tenantFilter is NOT active; relying on {@link #findBySubjectName}
     * there leaks across tenants. This query filters by tenant_id explicitly and is safe in the
     * seeder context.
     */
    @Query("select a from AcademicSubject a where a.subjectName = :name and a.tenantId = :tenantId")
    Optional<AcademicSubject> findBySubjectNameAndTenant(@Param("name") String name,
                                                         @Param("tenantId") String tenantId);

    /** Tenant-explicit existence check by id (avoids cross-tenant matches during seeding). */
    @Query("select count(a) > 0 from AcademicSubject a where a.id = :id and a.tenantId = :tenantId")
    boolean existsByIdAndTenant(@Param("id") Long id, @Param("tenantId") String tenantId);
}
