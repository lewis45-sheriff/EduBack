package com.EduePoa.EP.OrgUnit.Repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.OrgUnit.Entity.OrgUnit;
import com.EduePoa.EP.OrgUnit.Entity.OrgUnitStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrgUnitRepository extends TenantAwareRepository<OrgUnit, Long> {

    Optional<OrgUnit> findByCode(String code);

    boolean existsByCode(String code);

    List<OrgUnit> findByParentId(Long parentId);

    List<OrgUnit> findByStatus(OrgUnitStatus status);

    /** The active root (no parent) for the current tenant, if any. */
    @Query("select u from OrgUnit u where u.parent is null and u.status = :status")
    List<OrgUnit> findRoots(@Param("status") OrgUnitStatus status);

    /**
     * Whole subtree of a unit (the unit itself + all descendants), matched by materialized path
     * prefix. {@code pathPrefix} is the unit's own path, e.g. "/1/4/".
     */
    @Query("select u from OrgUnit u where u.path like concat(:pathPrefix, '%')")
    List<OrgUnit> findSubtreeByPathPrefix(@Param("pathPrefix") String pathPrefix);

    long countByParentIdAndStatus(Long parentId, OrgUnitStatus status);

    // ---- Tenant-explicit checks for seeding/backfill (do not rely on the request filter) ----

    boolean existsByTenantIdAndCode(String tenantId, String code);

    @Query("select u from OrgUnit u where u.tenantId = :tenantId and u.parent is null and u.status = :status")
    List<OrgUnit> findRootsForTenant(@Param("tenantId") String tenantId,
                                     @Param("status") OrgUnitStatus status);
}
