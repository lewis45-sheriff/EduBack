package com.EduePoa.EP.Transport.TransportTransactions.Approval;

import com.EduePoa.EP.Transport.TransportTransactions.Approval.Request.RejectTransportPaymentRequest;
import com.EduePoa.EP.Transport.TransportTransactions.Approval.Request.SubmitTransportPaymentRequest;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("api/v1/transport/transactions")
@RequiredArgsConstructor
public class PendingTransportTransactionController {

    private final PendingTransportTransactionService service;

    @PreAuthorize("hasPermission(null, 'transport_transaction:create')")
    @PostMapping("/submit/{studentId}")
    ResponseEntity<?> submit(@PathVariable Long studentId,
                             @RequestBody SubmitTransportPaymentRequest request) {
        CustomResponse<?> response = service.submit(studentId, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PreAuthorize("hasPermission(null, 'transport_transaction:approve')")
    @PutMapping("/approve/{id}")
    ResponseEntity<?> approve(@PathVariable Long id) {
        CustomResponse<?> response = service.approve(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PreAuthorize("hasPermission(null, 'transport_transaction:approve')")
    @PutMapping("/reject/{id}")
    ResponseEntity<?> reject(@PathVariable Long id,
                             @RequestBody(required = false) RejectTransportPaymentRequest request) {
        String reason = request != null ? request.getReason() : null;
        CustomResponse<?> response = service.reject(id, reason);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PreAuthorize("hasPermission(null, 'transport_transaction:create') or hasPermission(null, 'transport_transaction:approve')")
    @GetMapping
    ResponseEntity<?> list(@RequestParam(required = false) PendingTransportTransactionStatus status) {
        CustomResponse<?> response = service.list(status);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PreAuthorize("hasPermission(null, 'transport_transaction:create') or hasPermission(null, 'transport_transaction:approve')")
    @GetMapping("/{id}")
    ResponseEntity<?> get(@PathVariable Long id) {
        CustomResponse<?> response = service.get(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
