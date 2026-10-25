package com.EduePoa.EP.GeneralLedger.service;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.GeneralLedger.dto.GlReversalRequestDTO;
import com.EduePoa.EP.GeneralLedger.entity.GlReversalRequest;
import com.EduePoa.EP.GeneralLedger.entity.JournalEntry;
import com.EduePoa.EP.GeneralLedger.entity.JournalLine;
import com.EduePoa.EP.GeneralLedger.enums.GlReversalStatus;
import com.EduePoa.EP.GeneralLedger.enums.JournalStatus;
import com.EduePoa.EP.GeneralLedger.repository.GlReversalRequestRepository;
import com.EduePoa.EP.GeneralLedger.repository.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Maker-checker reversal of a GL journal, modelled on
 * {@code TransactionReversalServiceImpl}:
 * <ol>
 *   <li>a maker requests a reversal (stored PENDING_APPROVAL, no accounting effect);</li>
 *   <li>a DIFFERENT checker approves (creates exactly one counter-journal and marks
 *       the original REVERSED) or rejects (no effect).</li>
 * </ol>
 * Maker-checker separation is enforced here on the backend, not just the UI. The
 * approve transition is guarded by the request status, the entity {@code @Version}
 * (optimistic lock), and the DB-unique counter-journal, so two simultaneous
 * approvals cannot both create a reversal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GlReversalService {

    private final GlReversalRequestRepository reversalRepository;
    private final JournalEntryRepository journalRepository;
    private final GeneralLedgerService generalLedgerService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    // ---- Create (maker) -----------------------------------------------------

    public GlReversalRequestDTO createRequest(Long journalId, String reason) {
        if (journalId == null) {
            throw new GlException("journalId is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new GlException("A reason is required to request a reversal");
        }
        JournalEntry target = journalRepository.findById(journalId)
                .orElseThrow(() -> new GlException("Journal not found: " + journalId));

        if (target.getStatus() == JournalStatus.REVERSED) {
            throw new GlException("Journal " + target.getEntryNumber() + " is already reversed");
        }
        if (target.getReversalOfJournalId() != null) {
            throw new GlException("A reversal counter-journal cannot itself be reversed");
        }
        boolean already = reversalRepository.existsByTargetJournalIdAndStatusIn(journalId,
                List.of(GlReversalStatus.PENDING_APPROVAL, GlReversalStatus.APPROVED));
        if (already) {
            throw new GlException("A reversal for journal " + target.getEntryNumber()
                    + " is already pending or applied");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (JournalLine l : target.getLines()) {
            total = total.add(l.getDebit() != null ? l.getDebit() : BigDecimal.ZERO);
        }

        User maker = getCurrentUser();
        GlReversalRequest request = GlReversalRequest.builder()
                .targetJournalId(target.getId())
                .targetEntryNumber(target.getEntryNumber())
                .targetDescription(target.getDescription())
                .targetTotal(total)
                .targetSourceType(target.getSourceType() != null ? target.getSourceType().name() : null)
                .reason(reason)
                .status(GlReversalStatus.PENDING_APPROVAL)
                .createdBy(maker)
                .createdAt(LocalDateTime.now())
                .build();
        GlReversalRequest saved = reversalRepository.save(request);
        auditService.log("GL_REVERSAL", "Requested reversal ID:", String.valueOf(saved.getId()),
                "journal:", target.getEntryNumber());
        return GlMapper.toReversalDto(saved);
    }

    // ---- Approve (checker) --------------------------------------------------

    @Transactional
    public GlReversalRequestDTO approve(Long requestId) {
        GlReversalRequest request = reversalRepository.findById(requestId)
                .orElseThrow(() -> new GlException("Reversal request not found: " + requestId));
        if (request.getStatus() != GlReversalStatus.PENDING_APPROVAL) {
            throw new GlException("Reversal " + requestId + " is not pending approval (status: "
                    + request.getStatus() + ")");
        }
        User checker = getCurrentUser();
        if (request.getCreatedBy() != null && request.getCreatedBy().getId() != null
                && request.getCreatedBy().getId().equals(checker.getId())) {
            throw new GlException("The maker of a reversal cannot approve it. A different approver is required.");
        }

        JournalEntry original = journalRepository.findById(request.getTargetJournalId())
                .orElseThrow(() -> new GlException("Target journal no longer exists: "
                        + request.getTargetJournalId()));

        JournalEntry counter = generalLedgerService.reverse(
                original, request.getReason(), checker, checker.getUsername());

        request.setStatus(GlReversalStatus.APPROVED);
        request.setCounterJournalId(counter.getId());
        request.setDecidedBy(checker);
        request.setDecidedAt(LocalDateTime.now());
        GlReversalRequest saved = reversalRepository.save(request);
        auditService.log("GL_REVERSAL", "Approved reversal ID:", String.valueOf(saved.getId()),
                "counterJournal:", counter.getEntryNumber(), "approvedBy:", checker.getUsername());
        return GlMapper.toReversalDto(saved);
    }

    // ---- Reject (checker) ---------------------------------------------------

    @Transactional
    public GlReversalRequestDTO reject(Long requestId, String reason) {
        GlReversalRequest request = reversalRepository.findById(requestId)
                .orElseThrow(() -> new GlException("Reversal request not found: " + requestId));
        if (request.getStatus() != GlReversalStatus.PENDING_APPROVAL) {
            throw new GlException("Reversal " + requestId + " is not pending approval (status: "
                    + request.getStatus() + ")");
        }
        User checker = getCurrentUser();
        request.setStatus(GlReversalStatus.REJECTED);
        request.setDecidedBy(checker);
        request.setDecidedAt(LocalDateTime.now());
        request.setDecisionReason(reason);
        GlReversalRequest saved = reversalRepository.save(request);
        auditService.log("GL_REVERSAL", "Rejected reversal ID:", String.valueOf(saved.getId()),
                "rejectedBy:", checker.getUsername());
        return GlMapper.toReversalDto(saved);
    }

    // ---- Reads --------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<GlReversalRequestDTO> list(GlReversalStatus status) {
        List<GlReversalRequest> requests = (status == null)
                ? reversalRepository.findAllByOrderByCreatedAtDesc()
                : reversalRepository.findByStatusOrderByCreatedAtDesc(status);
        return requests.stream().map(GlMapper::toReversalDto).toList();
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new GlException("No authenticated user found");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new GlException("Current user not found: " + auth.getName()));
    }
}
