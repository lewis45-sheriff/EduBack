package com.EduePoa.EP.GeneralLedger.service.posting;

import com.EduePoa.EP.GeneralLedger.entity.GlPostingRequest;
import com.EduePoa.EP.GeneralLedger.enums.GlPostingStatus;
import com.EduePoa.EP.GeneralLedger.enums.GlSourceType;
import com.EduePoa.EP.GeneralLedger.repository.GlPostingRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write side of the transactional outbox.
 * <p>
 * {@link #enqueue} inserts a {@code PENDING} {@link GlPostingRequest} in the
 * SAME transaction as the finalized money event (default propagation). It is an
 * insert guarded by the {@code (tenant_id, source_type, source_id)} unique
 * constraint, so: (a) it cannot roll back the business write for any reason other
 * than its own persistence, and (b) a duplicate callback or concurrent retry can
 * never create a second request. If the row already exists, enqueue is a no-op.
 * <p>
 * A committed business event therefore always leaves a durable instruction to
 * post, which the {@link GlPostingProcessor} fulfils (inline, by sweeper, or by
 * manual retry) — closing the gap that a bare {@code REQUIRES_NEW} best-effort
 * post would leave open.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GlPostingOutbox {

    private final GlPostingRequestRepository postingRepository;
    private final ObjectMapper objectMapper;

    /**
     * Records the intent to post a journal for a finalized source event. Call this
     * from inside the business transaction, after the business row is saved.
     *
     * @return the id of the (new or pre-existing) posting request, or null if the
     *         payload could not be serialized (logged; never throws into the
     *         business transaction).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long enqueue(GlSourceType sourceType, Long sourceId, GlPostingPayload payload) {
        try {
            GlPostingRequest existing = postingRepository
                    .findBySourceTypeAndSourceId(sourceType, sourceId)
                    .orElse(null);
            if (existing != null) {
                return existing.getId();
            }
            String json = objectMapper.writeValueAsString(payload);
            GlPostingRequest request = GlPostingRequest.builder()
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .status(GlPostingStatus.PENDING)
                    .attempts(0)
                    .payload(json)
                    .build();
            return postingRepository.save(request).getId();
        } catch (DataIntegrityViolationException dive) {
            // Concurrent enqueue won the race; resolve to the existing row.
            return postingRepository.findBySourceTypeAndSourceId(sourceType, sourceId)
                    .map(GlPostingRequest::getId)
                    .orElse(null);
        } catch (Exception e) {
            // Serialization or unexpected error: do NOT break the money event. The
            // reconciliation report will still detect a finalized event with no
            // journal, so the gap is recoverable rather than silently lost.
            log.error("Failed to enqueue GL posting for {}#{}: {}", sourceType, sourceId, e.getMessage(), e);
            return null;
        }
    }
}
