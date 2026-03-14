package com.EduePoa.EP.Procurement.SupplierDashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/supplier-dashboard")
@RequiredArgsConstructor
public class SupplierDashboardController {

    private final SupplierDashboardService dashboardService;

    @GetMapping("/summary/{supplierId}")
    @PreAuthorize("hasPermission(null, 'supplier_payment:read')")
    public ResponseEntity<?> getSummary(@PathVariable Long supplierId) {
        var response = dashboardService.getSummary(supplierId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
