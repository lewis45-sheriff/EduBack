package com.EduePoa.EP.HR.config.controller;

import com.EduePoa.EP.HR.config.dto.*;
import com.EduePoa.EP.HR.config.service.StatutoryConfigAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Admin management of effective-dated statutory configuration. Viewing the resolved config
 * requires {@code payroll:read}; every write requires {@code payroll:config}. Writes create new
 * effective-dated rows, so historical runs (which read their frozen snapshot) are unaffected.
 */
@RestController
@RequestMapping("/api/v1/hr/statutory")
@RequiredArgsConstructor
public class StatutoryConfigController {

    private final StatutoryConfigAdminService service;

    @GetMapping("/config")
    @PreAuthorize("hasPermission(null, 'payroll:read') or hasPermission(null, 'payroll:config') "
            + "or hasPermission(null, 'payroll:manage')")
    public ResponseEntity<?> getResolved(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        var r = service.getResolved(asOf);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }

    @PutMapping("/paye-bands")
    @PreAuthorize("hasPermission(null, 'payroll:config')")
    public ResponseEntity<?> updatePayeBands(@Valid @RequestBody PayeBandsUpdateRequest request) {
        var r = service.updatePayeBands(request);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }

    @PutMapping("/nssf")
    @PreAuthorize("hasPermission(null, 'payroll:config')")
    public ResponseEntity<?> updateNssf(@Valid @RequestBody NssfConfigRequest request) {
        var r = service.updateNssf(request);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }

    @PutMapping("/shif")
    @PreAuthorize("hasPermission(null, 'payroll:config')")
    public ResponseEntity<?> updateShif(@Valid @RequestBody ShifConfigRequest request) {
        var r = service.updateShif(request);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }

    @PutMapping("/housing-levy")
    @PreAuthorize("hasPermission(null, 'payroll:config')")
    public ResponseEntity<?> updateHousingLevy(@Valid @RequestBody HousingLevyConfigRequest request) {
        var r = service.updateHousingLevy(request);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }

    @PutMapping("/nita")
    @PreAuthorize("hasPermission(null, 'payroll:config')")
    public ResponseEntity<?> updateNita(@Valid @RequestBody NitaConfigRequest request) {
        var r = service.updateNita(request);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }

    @PutMapping("/relief")
    @PreAuthorize("hasPermission(null, 'payroll:config')")
    public ResponseEntity<?> updateRelief(@Valid @RequestBody ReliefConfigRequest request) {
        var r = service.updateRelief(request);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }
}
