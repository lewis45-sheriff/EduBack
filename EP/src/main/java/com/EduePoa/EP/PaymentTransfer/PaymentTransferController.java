package com.EduePoa.EP.PaymentTransfer;

import com.EduePoa.EP.PaymentTransfer.Request.CreatePaymentTransferRequest;
import com.EduePoa.EP.PaymentTransfer.Request.RejectPaymentTransferRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/payment-transfers")
@RequiredArgsConstructor
public class PaymentTransferController {

    private final PaymentTransferService paymentTransferService;

    @PreAuthorize("hasPermission(null, 'payment_transfer:create')")
    @PostMapping("/create")
    ResponseEntity<?> create(@RequestBody CreatePaymentTransferRequest request) {
        var response = paymentTransferService.createTransfer(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PreAuthorize("hasPermission(null, 'payment_transfer:approve')")
    @PutMapping("/approve/{id}")
    ResponseEntity<?> approve(@PathVariable Long id) {
        var response = paymentTransferService.approveTransfer(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PreAuthorize("hasPermission(null, 'payment_transfer:approve')")
    @PutMapping("/reject/{id}")
    ResponseEntity<?> reject(@PathVariable Long id,
                             @RequestBody(required = false) RejectPaymentTransferRequest request) {
        String reason = request != null ? request.getReason() : null;
        var response = paymentTransferService.rejectTransfer(id, reason);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PreAuthorize("hasPermission(null, 'payment_transfer:create') or hasPermission(null, 'payment_transfer:approve')")
    @GetMapping
    ResponseEntity<?> list(@RequestParam(required = false) PaymentTransferStatus status) {
        var response = paymentTransferService.listTransfers(status);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PreAuthorize("hasPermission(null, 'payment_transfer:create') or hasPermission(null, 'payment_transfer:approve')")
    @GetMapping("/{id}")
    ResponseEntity<?> get(@PathVariable Long id) {
        var response = paymentTransferService.getTransfer(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
