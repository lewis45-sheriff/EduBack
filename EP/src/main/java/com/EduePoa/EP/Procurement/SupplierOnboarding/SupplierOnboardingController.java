package com.EduePoa.EP.Procurement.SupplierOnboarding;

import com.EduePoa.EP.Procurement.SupplierOnboarding.Requests.SupplierOnboardingRequestDTO;
import com.EduePoa.EP.Procurement.SupplierOnboarding.Requests.SupplierRejectionRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/suppliers-onboarding")
@RequiredArgsConstructor
public class SupplierOnboardingController {
    private final SupplierOnboardingService supplierOnboardingService;

    @PostMapping("/register-supplier")
    @PreAuthorize("hasPermission(null, 'supplier:create')")
    public ResponseEntity<?> createStudent(@Valid @RequestBody SupplierOnboardingRequestDTO request) {
        var response = supplierOnboardingService.registerSupplier(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("get-by-id/{id}")
    @PreAuthorize("hasPermission(null, 'supplier:read')")
    public ResponseEntity<?> getSupplier(@PathVariable Long id) {
        var response = supplierOnboardingService.getSupplierById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/get-all")
    @PreAuthorize("hasPermission(null, 'supplier:read')")
    public ResponseEntity<?> getAllSuppliers() {
        var response = supplierOnboardingService.getAllSuppliers();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasPermission(null, 'supplier:update')")
    public ResponseEntity<?> updateSupplier(@PathVariable Long id,
            @Valid @RequestBody SupplierOnboardingRequestDTO request) {
        var response = supplierOnboardingService.updateSupplier(id, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasPermission(null, 'supplier:delete')")
    public ResponseEntity<?> deleteSupplier(@PathVariable Long id) {
        var response = supplierOnboardingService.deleteSupplier(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PatchMapping("approve-supplier/{id}")
    @PreAuthorize("hasPermission(null, 'supplier:approve')")
    public ResponseEntity<?> approveSupplier(@PathVariable Long id) {
        var response = supplierOnboardingService.approveSupplier(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PatchMapping("reject-supplier/{id}")
    @PreAuthorize("hasPermission(null, 'supplier:reject')")
    public ResponseEntity<?> rejectSupplier(@PathVariable Long id,
            @Valid @RequestBody SupplierRejectionRequestDTO request) {
        var response = supplierOnboardingService.rejectSupplier(id, request.getRejectionReason());
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

}
