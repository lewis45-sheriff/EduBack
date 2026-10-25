package com.EduePoa.EP.GeneralLedger.dto;

import lombok.Builder;

import java.util.List;

/**
 * Snapshot of GL posting health for a tenant: outstanding outbox requests and
 * whether the posted journals reconcile (trial balance balances). Designed to
 * surface finalized events that have not posted, failures needing retry, and an
 * overall integrity flag — without creating any speculative correcting entries.
 */
@Builder
public record ReconciliationReportDTO(
        long pendingCount,
        long failedCount,
        long postedCount,
        boolean trialBalanceBalanced,
        List<GlPostingRequestDTO> outstanding
) {
}
