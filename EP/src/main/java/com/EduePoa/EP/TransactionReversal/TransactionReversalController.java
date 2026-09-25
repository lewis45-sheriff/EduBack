package com.EduePoa.EP.TransactionReversal;

import com.EduePoa.EP.TransactionReversal.Request.CreateReversalRequest;
import com.EduePoa.EP.TransactionReversal.Request.RejectReversalRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/transaction-reversals")
@RequiredArgsConstructor
public class TransactionReversalController {

    private final TransactionReversalService transactionReversalService;

    @PreAuthorize("hasPermission(null, 'transaction_reversal:create')")
    @PostMapping("/create/{transactionId}")
    ResponseEntity<?> create(@PathVariable Long transactionId,
                             @RequestBody(required = false) CreateReversalRequest request) {
        String reason = request != null ? request.getReason() : null;
        var response = transactionReversalService.createReversal(transactionId, reason);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PreAuthorize("hasPermission(null, 'transaction_reversal:approve')")
    @PutMapping("/approve/{id}")
    ResponseEntity<?> approve(@PathVariable Long id) {
        var response = transactionReversalService.approveReversal(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PreAuthorize("hasPermission(null, 'transaction_reversal:approve')")
    @PutMapping("/reject/{id}")
    ResponseEntity<?> reject(@PathVariable Long id,
                             @RequestBody(required = false) RejectReversalRequest request) {
        String reason = request != null ? request.getReason() : null;
        var response = transactionReversalService.rejectReversal(id, reason);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PreAuthorize("hasPermission(null, 'transaction_reversal:create') or hasPermission(null, 'transaction_reversal:approve')")
    @GetMapping
    ResponseEntity<?> list(@RequestParam(required = false) TransactionReversalStatus status) {
        var response = transactionReversalService.listReversals(status);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PreAuthorize("hasPermission(null, 'transaction_reversal:create') or hasPermission(null, 'transaction_reversal:approve')")
    @GetMapping("/{id}")
    ResponseEntity<?> get(@PathVariable Long id) {
        var response = transactionReversalService.getReversal(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
