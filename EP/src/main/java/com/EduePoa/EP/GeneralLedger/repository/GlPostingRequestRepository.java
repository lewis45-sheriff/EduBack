package com.EduePoa.EP.GeneralLedger.repository;

import com.EduePoa.EP.GeneralLedger.entity.GlPostingRequest;
import com.EduePoa.EP.GeneralLedger.enums.GlPostingStatus;
import com.EduePoa.EP.GeneralLedger.enums.GlSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GlPostingRequestRepository extends JpaRepository<GlPostingRequest, Long> {

    Optional<GlPostingRequest> findBySourceTypeAndSourceId(GlSourceType sourceType, Long sourceId);

    boolean existsBySourceTypeAndSourceId(GlSourceType sourceType, Long sourceId);

    /** Tenant-scoped (filter-applied) unprocessed requests, for the sweeper/retry UI. */
    List<GlPostingRequest> findByStatusInOrderByCreatedAtAsc(List<GlPostingStatus> statuses);

    List<GlPostingRequest> findByStatusOrderByCreatedAtAsc(GlPostingStatus status);

    /**
     * Cross-tenant scan of outstanding requests for the scheduled sweeper, which
     * runs without a tenant context. Returns only ids + tenant so the processor
     * can re-establish each request's tenant before posting. Native/explicit to
     * bypass the request-scoped tenant filter safely.
     */
    @Query("select p.id as id, p.tenantId as tenantId from GlPostingRequest p where p.status in :statuses order by p.createdAt asc")
    List<SweepRow> findOutstandingForSweep(@Param("statuses") List<GlPostingStatus> statuses);

    interface SweepRow {
        Long getId();
        String getTenantId();
    }
}
