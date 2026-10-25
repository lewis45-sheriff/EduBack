package com.EduePoa.EP.GeneralLedger.service.posting;

import com.EduePoa.EP.GeneralLedger.entity.GlPostingRequest;
import com.EduePoa.EP.GeneralLedger.entity.JournalEntry;
import com.EduePoa.EP.GeneralLedger.enums.GlPostingStatus;
import com.EduePoa.EP.GeneralLedger.repository.GlPostingRequestRepository;
import com.EduePoa.EP.GeneralLedger.service.GeneralLedgerService;
import com.EduePoa.EP.GeneralLedger.service.command.PostJournalCommand;
import com.EduePoa.EP.Multitenancy.config.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the transactional outbox: turns a {@link GlPostingRequest} into a
 * journal, idempotently.
 * <p>
 * Each request is processed in its OWN {@code REQUIRES_NEW} transaction so a
 * posting failure is isolated: the request is marked {@code FAILED} with the
 * error and the attempt count incremented (never discarded), while the business
 * event that enqueued it is unaffected. Success marks it {@code POSTED} and links
 * the journal id. Because {@link GeneralLedgerService#post} is itself idempotent
 * on {@code (sourceType, sourceId)}, re-processing a request can never create a
 * duplicate journal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GlPostingProcessor {

    private final GlPostingRequestRepository postingRepository;
    private final GeneralLedgerService generalLedgerService;
    private final GlPostingRules postingRules;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Processes a single posting request by id in a new transaction. Returns true
     * when the request ends in {@code POSTED}. Any exception is caught, recorded on
     * the request, and swallowed so callers (inline hook, sweeper, retry endpoint)
     * are never broken by a single bad request.
     * <p>
     * Because this runs in its OWN {@code REQUIRES_NEW} transaction (a fresh
     * Hibernate session), it cannot rely on an ambient request-scoped tenant filter.
     * It therefore re-establishes the tenant context AND the Hibernate tenant filter
     * from the request's own {@code tenantId}, so account resolution and all writes
     * are correctly scoped to that tenant regardless of the caller (inline
     * after-commit hook, scheduled sweeper, or manual retry).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean process(Long requestId) {
        GlPostingRequest request = postingRepository.findById(requestId).orElse(null);
        if (request == null) {
            return false;
        }
        if (request.getStatus() == GlPostingStatus.POSTED) {
            return true;
        }

        final String tenantId = request.getTenantId();
        final String previousTenant = TenantContext.getCurrentTenant();
        boolean filterEnabledHere = false;
        try {
            // Scope this new transaction/session to the request's tenant.
            if (tenantId != null && !tenantId.isBlank()) {
                TenantContext.setCurrentTenant(tenantId);
                filterEnabledHere = enableTenantFilter(tenantId);
            }

            GlPostingPayload payload = objectMapper.readValue(request.getPayload(), GlPostingPayload.class);
            PostJournalCommand command = postingRules.build(
                    request.getSourceType(), request.getSourceId(), payload,
                    null, "SYSTEM:gl-posting");
            JournalEntry journal = generalLedgerService.post(command);

            request.setStatus(GlPostingStatus.POSTED);
            request.setJournalId(journal.getId());
            request.setLastError(null);
            request.setPostedAt(java.time.LocalDateTime.now());
            postingRepository.save(request);
            log.info("GL posting {} -> journal {} ({}#{})", request.getId(),
                    journal.getEntryNumber(), request.getSourceType(), request.getSourceId());
            return true;
        } catch (Exception e) {
            request.setStatus(GlPostingStatus.FAILED);
            request.setAttempts(request.getAttempts() + 1);
            request.setLastError(truncate(e.getMessage()));
            postingRepository.save(request);
            log.error("GL posting {} FAILED ({}#{}): {}", request.getId(),
                    request.getSourceType(), request.getSourceId(), e.getMessage());
            return false;
        } finally {
            if (filterEnabledHere) {
                disableTenantFilter();
            }
            if (previousTenant != null) {
                TenantContext.setCurrentTenant(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    private boolean enableTenantFilter(String tenantId) {
        try {
            entityManager.unwrap(Session.class).enableFilter("tenantFilter").setParameter("tenantId", tenantId);
            return true;
        } catch (Exception e) {
            // Filter already enabled on this session (e.g. inline within a request); fine.
            return false;
        }
    }

    private void disableTenantFilter() {
        try {
            entityManager.unwrap(Session.class).disableFilter("tenantFilter");
        } catch (Exception ignored) {
            // not enabled
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "Unknown error";
        }
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
