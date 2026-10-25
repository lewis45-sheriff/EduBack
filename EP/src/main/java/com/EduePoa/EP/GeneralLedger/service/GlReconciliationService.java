package com.EduePoa.EP.GeneralLedger.service;

import com.EduePoa.EP.GeneralLedger.dto.GlPostingRequestDTO;
import com.EduePoa.EP.GeneralLedger.dto.ReconciliationReportDTO;
import com.EduePoa.EP.GeneralLedger.entity.GlPostingRequest;
import com.EduePoa.EP.GeneralLedger.enums.GlPostingStatus;
import com.EduePoa.EP.GeneralLedger.repository.GlPostingRequestRepository;
import com.EduePoa.EP.GeneralLedger.service.posting.GlPostingProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Reconciliation and recovery of GL postings.
 * <p>
 * The report exposes outstanding (PENDING/FAILED) outbox requests, posted counts,
 * and whether the trial balance reconciles — the signals an operator needs to find
 * finalized events that have not posted, failures needing retry, and an overall
 * integrity problem. It never fabricates correcting journals; a retry simply
 * re-runs the idempotent processor against the durable outbox row.
 */
@Service
@RequiredArgsConstructor
public class GlReconciliationService {

    private final GlPostingRequestRepository postingRepository;
    private final GlPostingProcessor postingProcessor;
    private final GlQueryService queryService;

    @Transactional(readOnly = true)
    public ReconciliationReportDTO report() {
        List<GlPostingRequest> outstanding = postingRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(GlPostingStatus.PENDING, GlPostingStatus.FAILED));
        long pending = outstanding.stream().filter(r -> r.getStatus() == GlPostingStatus.PENDING).count();
        long failed = outstanding.stream().filter(r -> r.getStatus() == GlPostingStatus.FAILED).count();
        long posted = postingRepository.findByStatusOrderByCreatedAtAsc(GlPostingStatus.POSTED).size();
        boolean balanced = queryService.trialBalance(LocalDate.now()).balanced();

        List<GlPostingRequestDTO> dtos = outstanding.stream().map(GlMapper::toPostingDto).toList();
        return ReconciliationReportDTO.builder()
                .pendingCount(pending)
                .failedCount(failed)
                .postedCount(posted)
                .trialBalanceBalanced(balanced)
                .outstanding(dtos)
                .build();
    }

    @Transactional(readOnly = true)
    public List<GlPostingRequestDTO> listOutstanding() {
        return postingRepository.findByStatusInOrderByCreatedAtAsc(
                        List.of(GlPostingStatus.PENDING, GlPostingStatus.FAILED))
                .stream().map(GlMapper::toPostingDto).toList();
    }

    /**
     * Manually retry a single posting request. Delegates to the idempotent
     * processor (its own REQUIRES_NEW transaction), so a retry can never duplicate
     * an already-posted journal.
     */
    public boolean retry(Long requestId) {
        GlPostingRequest request = postingRepository.findById(requestId)
                .orElseThrow(() -> new GlException("Posting request not found: " + requestId));
        if (request.getStatus() == GlPostingStatus.POSTED) {
            return true;
        }
        return postingProcessor.process(requestId);
    }
}
