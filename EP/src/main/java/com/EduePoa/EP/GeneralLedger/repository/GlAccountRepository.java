package com.EduePoa.EP.GeneralLedger.repository;

import com.EduePoa.EP.GeneralLedger.entity.GlAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the chart of accounts. All queries are implicitly tenant-scoped
 * by the Hibernate {@code tenantFilter} enabled per request.
 */
public interface GlAccountRepository extends JpaRepository<GlAccount, Long> {

    Optional<GlAccount> findBySystemKey(String systemKey);

    Optional<GlAccount> findByCode(String code);

    boolean existsBySystemKey(String systemKey);

    boolean existsByCode(String code);

    /**
     * Explicitly tenant-scoped existence check for seeding. Does NOT rely on the
     * Hibernate request-filter (which is not reliably active in an
     * {@code ApplicationRunner}/provisioning context), so it correctly returns
     * false for a tenant that has no accounts even when other tenants do.
     */
    boolean existsByTenantIdAndSystemKey(String tenantId, String systemKey);

    boolean existsByTenantIdAndCode(String tenantId, String code);

    List<GlAccount> findAllByOrderByCodeAsc();

    @Query("select a from GlAccount a where (:activeOnly = false or a.active = true) order by a.code asc")
    List<GlAccount> findForListing(@Param("activeOnly") boolean activeOnly);
}
