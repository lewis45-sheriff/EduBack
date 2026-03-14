package com.EduePoa.EP.Procurement.SupplierInvoice;


import com.EduePoa.EP.Authentication.Enum.InvoiceStatus;
import com.EduePoa.EP.Procurement.SupplierInvoice.Request.SupplierInvoiceApprovalRequestDTO;
import com.EduePoa.EP.Procurement.SupplierInvoice.Request.SupplierInvoiceRequestDTO;
import com.EduePoa.EP.Procurement.SupplierInvoice.Request.SupplierInvoiceUploadRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/supplier-invoices")
@RequiredArgsConstructor
public class SupplierInvoiceController {

    private final SupplierInvoiceService supplierInvoiceService;

    @PostMapping("/create")
    @PreAuthorize("hasPermission(null, 'invoice:upload')")
    public ResponseEntity<?> create(@Valid @RequestBody SupplierInvoiceRequestDTO requestDTO) {
        var response = supplierInvoiceService.create(requestDTO);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/get-all")
    @PreAuthorize("hasPermission(null, 'invoice:read')")
    public ResponseEntity<?> getAll() {
        var response = supplierInvoiceService.getAll();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("get-by-id/{id}")
    @PreAuthorize("hasPermission(null, 'invoice:read')")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        var response = supplierInvoiceService.getById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasPermission(null, 'invoice:read')")
    public ResponseEntity<?> getByStatus(@PathVariable InvoiceStatus status) {
        var response = supplierInvoiceService.getByStatus(status);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PatchMapping("/edit/{id}")
    @PreAuthorize("hasPermission(null, 'invoice:update')")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody SupplierInvoiceRequestDTO requestDTO) {
        var response = supplierInvoiceService.update(id, requestDTO);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasPermission(null, 'invoice:update')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestParam InvoiceStatus status) {
        var response = supplierInvoiceService.updateStatus(id, status);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping("/edit/{id}")
    @PreAuthorize("hasPermission(null, 'invoice:delete')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        var response = supplierInvoiceService.delete(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/upload")
    @PreAuthorize("hasPermission(null, 'invoice:upload')")
    public ResponseEntity<?> uploadInvoice(@Valid @RequestBody SupplierInvoiceUploadRequestDTO request) {
        var response = supplierInvoiceService.uploadInvoice(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PatchMapping("/approve")
    @PreAuthorize("hasPermission(null, 'invoice:approve')")
    public ResponseEntity<?> approveInvoice(
            @Valid @RequestBody SupplierInvoiceApprovalRequestDTO request) {
        var response = supplierInvoiceService.approveInvoice(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PatchMapping("/reject")
    @PreAuthorize("hasPermission(null, 'invoice:reject')")
    public ResponseEntity<?> rejectInvoice(
            @Valid @RequestBody SupplierInvoiceApprovalRequestDTO request) {
        var response = supplierInvoiceService.rejectInvoice(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/invoice-per-supplier/{id}")
    @PreAuthorize("hasPermission(null, 'invoice:delete')")
    public ResponseEntity<?> InvoicePerSupplier(@PathVariable Long id) {
        var response = supplierInvoiceService.InvoicePerSupplier(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/supplier/{supplierId}")
    @PreAuthorize("hasPermission(null, 'invoice:read')")
    public ResponseEntity<?> getInvoicesBySupplier(
            @PathVariable Long supplierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "invoiceDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        var response = supplierInvoiceService.getBySupplier(supplierId, page, size, sortBy, sortDir);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}