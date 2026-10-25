package com.EduePoa.EP.GeneralLedger.controller;

import com.EduePoa.EP.GeneralLedger.dto.GlReversalRequestDTO;
import com.EduePoa.EP.GeneralLedger.dto.RejectReversalBody;
import com.EduePoa.EP.GeneralLedger.dto.ReversalRequestBody;
import com.EduePoa.EP.GeneralLedger.enums.GlReversalStatus;
import com.EduePoa.EP.GeneralLedger.service.GlException;
import com.EduePoa.EP.GeneralLedger.service.GlReversalService;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Maker-checker GL journal reversal endpoints. Create requires
 * {@code journal_reversal:create}; approve/reject require
 * {@code journal_reversal:approve}. Maker-checker separation is enforced in the
 * service, not here.
 */
@RestController
@RequestMapping("/api/v1/gl/reversals")
@RequiredArgsConstructor
public class GlReversalController {

    private final GlReversalService reversalService;

    @PostMapping("/{journalId}")
    @PreAuthorize("hasPermission(null, 'journal_reversal:create')")
    public ResponseEntity<CustomResponse<GlReversalRequestDTO>> create(
            @PathVariable Long journalId, @RequestBody(required = false) ReversalRequestBody body) {
        try {
            String reason = body != null ? body.reason() : null;
            GlReversalRequestDTO dto = reversalService.createRequest(journalId, reason);
            return response(HttpStatus.CREATED, "Reversal requested and pending approval", dto);
        } catch (GlException e) {
            return response(HttpStatus.BAD_REQUEST, e.getMessage(), null);
        }
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'gl:read')")
    public ResponseEntity<CustomResponse<List<GlReversalRequestDTO>>> list(
            @RequestParam(required = false) GlReversalStatus status) {
        return response(HttpStatus.OK, "Reversal requests retrieved", reversalService.list(status));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasPermission(null, 'journal_reversal:approve')")
    public ResponseEntity<CustomResponse<GlReversalRequestDTO>> approve(@PathVariable Long id) {
        try {
            return response(HttpStatus.OK, "Reversal approved and counter-journal posted",
                    reversalService.approve(id));
        } catch (GlException e) {
            return response(HttpStatus.CONFLICT, e.getMessage(), null);
        }
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasPermission(null, 'journal_reversal:approve')")
    public ResponseEntity<CustomResponse<GlReversalRequestDTO>> reject(
            @PathVariable Long id, @RequestBody(required = false) RejectReversalBody body) {
        try {
            String reason = body != null ? body.reason() : null;
            return response(HttpStatus.OK, "Reversal rejected", reversalService.reject(id, reason));
        } catch (GlException e) {
            return response(HttpStatus.CONFLICT, e.getMessage(), null);
        }
    }

    private <T> ResponseEntity<CustomResponse<T>> response(HttpStatus status, String message, T entity) {
        return ResponseEntity.status(status).body(new CustomResponse<>(message, status.value(), entity));
    }
}
