package com.EduePoa.EP.GeneralLedger.controller;

import com.EduePoa.EP.GeneralLedger.dto.GlPostingRequestDTO;
import com.EduePoa.EP.GeneralLedger.dto.ReconciliationReportDTO;
import com.EduePoa.EP.GeneralLedger.service.GlException;
import com.EduePoa.EP.GeneralLedger.service.GlReconciliationService;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Restricted reconciliation + recovery endpoints for failed/missing GL postings.
 * Gated on {@code gl:reconcile}. Retry is a controlled re-run of the idempotent
 * processor against a durable outbox row; it never edits journals or creates
 * speculative entries.
 */
@RestController
@RequestMapping("/api/v1/gl/reconciliation")
@RequiredArgsConstructor
public class GlReconciliationController {

    private final GlReconciliationService reconciliationService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'gl:reconcile')")
    public ResponseEntity<CustomResponse<ReconciliationReportDTO>> report() {
        return response(HttpStatus.OK, "Reconciliation report retrieved", reconciliationService.report());
    }

    @GetMapping("/outstanding")
    @PreAuthorize("hasPermission(null, 'gl:reconcile')")
    public ResponseEntity<CustomResponse<List<GlPostingRequestDTO>>> outstanding() {
        return response(HttpStatus.OK, "Outstanding postings retrieved",
                reconciliationService.listOutstanding());
    }

    @PostMapping("/{requestId}/retry")
    @PreAuthorize("hasPermission(null, 'gl:reconcile')")
    public ResponseEntity<CustomResponse<Boolean>> retry(@PathVariable Long requestId) {
        try {
            boolean posted = reconciliationService.retry(requestId);
            return response(posted ? HttpStatus.OK : HttpStatus.CONFLICT,
                    posted ? "Posting retried and succeeded" : "Retry did not succeed; see last error",
                    posted);
        } catch (GlException e) {
            return response(HttpStatus.BAD_REQUEST, e.getMessage(), false);
        }
    }

    private <T> ResponseEntity<CustomResponse<T>> response(HttpStatus status, String message, T entity) {
        return ResponseEntity.status(status).body(new CustomResponse<>(message, status.value(), entity));
    }
}
