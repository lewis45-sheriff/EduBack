package com.EduePoa.EP.GeneralLedger.service.posting;

import com.EduePoa.EP.GeneralLedger.enums.GlSourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The single entry point money-event integrations use to request a GL posting.
 * <p>
 * It enqueues the outbox row inside the caller's (business) transaction, then
 * registers an {@code afterCommit} synchronization so the posting is attempted
 * inline immediately after the business transaction commits — giving a journal
 * right away in the happy path, without ever risking the business transaction.
 * If inline processing fails or the app restarts first, the scheduled sweeper and
 * the manual retry endpoint still complete it from the durable outbox row.
 * <p>
 * The {@code afterCommit} callback runs on the same thread, so the request-scoped
 * tenant context is still in place when it processes the request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GlPostingGateway {

    private final GlPostingOutbox outbox;
    private final GlPostingProcessor processor;

    /**
     * Enqueue a posting for a finalized money event and schedule inline processing
     * after commit. Safe to call multiple times for the same source event
     * (idempotent). Never throws into the caller for posting problems — only a
     * failure to persist the outbox row itself (an insert) could propagate, and
     * that is handled defensively inside {@link GlPostingOutbox#enqueue}.
     */
    public void requestPosting(GlSourceType sourceType, Long sourceId, GlPostingPayload payload) {
        final Long requestId = outbox.enqueue(sourceType, sourceId, payload);
        if (requestId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        processor.process(requestId);
                    } catch (Exception e) {
                        // Durable row remains PENDING/FAILED for the sweeper; never
                        // let a post-commit hiccup surface to the committed caller.
                        log.warn("Inline GL posting after commit failed for request {}: {}",
                                requestId, e.getMessage());
                    }
                }
            });
        } else {
            // No active transaction (e.g. called outside a tx): process directly.
            try {
                processor.process(requestId);
            } catch (Exception e) {
                log.warn("Direct GL posting failed for request {}: {}", requestId, e.getMessage());
            }
        }
    }
}
