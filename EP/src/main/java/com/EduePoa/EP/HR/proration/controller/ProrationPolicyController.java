package com.EduePoa.EP.HR.proration.controller;

import com.EduePoa.EP.HR.proration.dto.ProrationPolicyRequest;
import com.EduePoa.EP.HR.proration.service.ProrationPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;


@RestController
@RequestMapping("/api/v1/hr/proration-policy")
@RequiredArgsConstructor
public class ProrationPolicyController {

    private final ProrationPolicyService service;

    @PostMapping({"", "/create"})
    @PreAuthorize("hasPermission(null, 'payroll:config')")
    public ResponseEntity<?> create(@Valid @RequestBody ProrationPolicyRequest request) {
        var response = service.createVersion(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping({"", "/versions"})
    @PreAuthorize("hasPermission(null, 'payroll:read') or hasPermission(null, 'payroll:config') "
            + "or hasPermission(null, 'payroll:manage')")
    public ResponseEntity<?> list() {
        var response = service.listVersions();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/effective")
    @PreAuthorize("hasPermission(null, 'payroll:read') or hasPermission(null, 'payroll:config') "
            + "or hasPermission(null, 'payroll:manage')")
    public ResponseEntity<?> effective(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        var response = service.getEffective(asOf);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
