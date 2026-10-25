package com.EduePoa.EP.GeneralLedger.service.posting;

import com.EduePoa.EP.GeneralLedger.enums.GlPostingStatus;
import com.EduePoa.EP.GeneralLedger.repository.GlPostingRequestRepository;
import com.EduePoa.EP.Multitenancy.config.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Recovery sweeper: periodically re-attempts any {@code PENDING} or {@code FAILED}
 * posting request that inline processing did not complete (e.g. a transient DB
 * error, or an application restart between the business commit and inline post).
 * <p>
 * It runs outside any HTTP request, so there is no ambient tenant context. For
 * each outstanding request it re-establishes that request's tenant (context +
 * Hibernate filter, exactly like the config seeders) before delegating to the
 * idempotent {@link GlPostingProcessor}. Idempotency guarantees that a request
 * already posted inline is never posted twice.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GlPostingSweeper {

    private final GlPostingRequestRepository postingRepository;
    private final GlPostingProcessor processor;

    @PersistenceContext
    private EntityManager entityManager;

    /** Every 5 minutes, after a 1-minute startup delay. */
    @Scheduled(fixedDelayString = "${gl.posting.sweep.delay-ms:300000}", initialDelay = 60000)
    public void sweep() {
        List<GlPostingRequestRepository.SweepRow> outstanding;
        try {
            outstanding = postingRepository.findOutstandingForSweep(
                    List.of(GlPostingStatus.PENDING, GlPostingStatus.FAILED));
        } catch (Exception e) {
            log.error("GL posting sweep query failed: {}", e.getMessage());
            return;
        }
        if (outstanding.isEmpty()) {
            return;
        }
        log.info("GL posting sweep: {} outstanding request(s)", outstanding.size());
        for (GlPostingRequestRepository.SweepRow row : outstanding) {
            String tenantId = row.getTenantId();
            if (tenantId == null || tenantId.isBlank()) {
                continue;
            }
            try {
                TenantContext.setCurrentTenant(tenantId);
                enableTenantFilter(tenantId);
                processor.process(row.getId());
            } catch (Exception e) {
                log.warn("GL posting sweep failed for request {} (tenant {}): {}",
                        row.getId(), tenantId, e.getMessage());
            } finally {
                disableTenantFilter();
                TenantContext.clear();
            }
        }
    }

    private void enableTenantFilter(String tenantId) {
        entityManager.unwrap(Session.class).enableFilter("tenantFilter").setParameter("tenantId", tenantId);
    }

    private void disableTenantFilter() {
        try {
            entityManager.unwrap(Session.class).disableFilter("tenantFilter");
        } catch (Exception ignored) {
            // not enabled
        }
    }
}
