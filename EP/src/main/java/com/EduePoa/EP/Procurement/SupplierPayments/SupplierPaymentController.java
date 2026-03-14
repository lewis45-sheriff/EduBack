package com.EduePoa.EP.Procurement.SupplierPayments;


import com.EduePoa.EP.Procurement.SupplierPayments.Requests.PaymentRequestDTO;
import com.EduePoa.EP.Procurement.SupplierPayments.Responses.PaymentResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/supplier-payments")
@RequiredArgsConstructor
public class SupplierPaymentController {

    private final SupplierPaymentService supplierPaymentService;

    @PostMapping("/record")
    @PreAuthorize("hasPermission(null, 'supplier_payment:create')")
    public ResponseEntity<?> recordPayment(@Valid @RequestBody PaymentRequestDTO request) {
        var response = supplierPaymentService.recordPayment(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/get-by-id/{id}")
    @PreAuthorize("hasPermission(null, 'supplier_payment:read')")
    public ResponseEntity<?> getPaymentById(@PathVariable Long id) {
        var response = supplierPaymentService.getPaymentById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/get-all")
    @PreAuthorize("hasPermission(null, 'supplier_payment:read')")
    public ResponseEntity<?> getAllPayments(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "id") String sortBy, @RequestParam(defaultValue = "DESC") String sortDir) {
        Sort.Direction direction = sortDir.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<PaymentResponseDTO> result = supplierPaymentService.getAllPayments(pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/supplier/{supplierId}")
    @PreAuthorize("hasPermission(null, 'supplier_payment:read')")
    public ResponseEntity<?> getPaymentsBySupplier(
            @PathVariable Long supplierId, 
            @RequestParam(defaultValue = "0") int page, 
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "paymentDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        Page<PaymentResponseDTO> result = supplierPaymentService.getPaymentsBySupplier(supplierId, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/invoice/{invoiceId}")
    @PreAuthorize("hasPermission(null, 'supplier_payment:read')")
    public ResponseEntity<?> getPaymentsByInvoice(@PathVariable Long invoiceId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<PaymentResponseDTO> result = supplierPaymentService.getPaymentsByInvoice(invoiceId, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/supplier/{supplierId}/balance")
    @PreAuthorize("hasPermission(null, 'supplier_payment:read')")
    public ResponseEntity<?> getSupplierBalance(@PathVariable Long supplierId) {
        var response = supplierPaymentService.getSupplierBalance(supplierId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping("/approve/{id}")
    @PreAuthorize("hasPermission(null, 'supplier_payment:approve')")
    public ResponseEntity<?> approvePayment(@PathVariable Long id) {
        var response = supplierPaymentService.approvePayment(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping("/reject/{id}")
    @PreAuthorize("hasPermission(null, 'supplier_payment:approve')")
    public ResponseEntity<?> rejectPayment(@PathVariable Long id, @RequestParam(required = false) String reason) {
        var response = supplierPaymentService.rejectPayment(id, reason);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping("/edit/{id}")
    @PreAuthorize("hasPermission(null, 'supplier_payment:create')")
    public ResponseEntity<?> editPayment(@PathVariable Long id, @Valid @RequestBody PaymentRequestDTO request) {
        var response = supplierPaymentService.editPayment(id, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
