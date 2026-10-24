package com.EduePoa.EP.HR.compensation.controller;

import com.EduePoa.EP.HR.compensation.dto.request.AllowanceRequest;
import com.EduePoa.EP.HR.compensation.dto.request.CompensationRequest;
import com.EduePoa.EP.HR.compensation.dto.request.RecurringDeductionRequest;
import com.EduePoa.EP.HR.compensation.service.CompensationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/hr/compensation")
@RequiredArgsConstructor
public class CompensationController {

    private final CompensationService compensationService;

    @PostMapping({"", "/save"})
    @PreAuthorize("hasPermission(null, 'payroll:manage')")
    public ResponseEntity<?> save(@Valid @RequestBody CompensationRequest request) {
        var response = compensationService.saveCompensation(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/staff/{staffId}")
    @PreAuthorize("hasPermission(null, 'payroll:read') or hasPermission(null, 'payroll:manage') "
            + "or hasPermission(null, 'staff:read')")
    public ResponseEntity<?> getByStaff(@PathVariable Long staffId) {
        var response = compensationService.getByStaffId(staffId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping({"", "/get-all"})
    @PreAuthorize("hasPermission(null, 'payroll:read') or hasPermission(null, 'payroll:manage')")
    public ResponseEntity<?> list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size) {
        var response = compensationService.list(page, size);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/staff/{staffId}/allowances")
    @PreAuthorize("hasPermission(null, 'payroll:manage')")
    public ResponseEntity<?> addAllowance(@PathVariable Long staffId,
                                          @Valid @RequestBody AllowanceRequest request) {
        var response = compensationService.addAllowance(staffId, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping("/staff/{staffId}/allowances/{allowanceId}")
    @PreAuthorize("hasPermission(null, 'payroll:manage')")
    public ResponseEntity<?> deleteAllowance(@PathVariable Long staffId, @PathVariable Long allowanceId) {
        var response = compensationService.deleteAllowance(staffId, allowanceId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/staff/{staffId}/deductions")
    @PreAuthorize("hasPermission(null, 'payroll:manage')")
    public ResponseEntity<?> addDeduction(@PathVariable Long staffId,
                                          @Valid @RequestBody RecurringDeductionRequest request) {
        var response = compensationService.addDeduction(staffId, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping("/staff/{staffId}/deductions/{deductionId}")
    @PreAuthorize("hasPermission(null, 'payroll:manage')")
    public ResponseEntity<?> deleteDeduction(@PathVariable Long staffId, @PathVariable Long deductionId) {
        var response = compensationService.deleteDeduction(staffId, deductionId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
