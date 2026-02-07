package com.EduePoa.EP.Authentication.AuditLogs;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
@RequestMapping("api/v1/audit")
@RequiredArgsConstructor
public class AuditController {
    private final AuditService auditService;

    /**
     * Get all audit logs with optional pagination
     */
    @GetMapping("/logs")
    public ResponseEntity<?> getAllAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var response = auditService.getAllAuditLogs(page, size);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    /**
     * Get audit logs by specific date
     */
    @GetMapping("/logs/by-date/{date}")
    public ResponseEntity<?> getAuditLogsByDate(
            @PathVariable("date") @DateTimeFormat(pattern = "yyyy-MM-dd") Date date) {
        var response = auditService.getAuditClass(date);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    /**
     * Get audit logs within a date range
     */
    @GetMapping("/logs/by-date-range")
    public ResponseEntity<?> getAuditLogsByDateRange(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        var response = auditService.getAuditLogsByDateRange(startDate, endDate);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    /**
     * Get audit logs by module (e.g., STUDENT, TRANSPORT, FINANCE)
     */
    @GetMapping("/logs/by-module/{module}")
    public ResponseEntity<?> getAuditLogsByModule(@PathVariable String module) {
        var response = auditService.getAuditLogsByModule(module);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    /**
     * Get audit logs by user email
     */
    @GetMapping("/logs/by-user/{email}")
    public ResponseEntity<?> getAuditLogsByUser(@PathVariable String email) {
        var response = auditService.getAuditLogsByUser(email);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    /**
     * Search audit logs by activity keyword
     */
    @GetMapping("/logs/search")
    public ResponseEntity<?> searchAuditLogs(@RequestParam String keyword) {
        var response = auditService.searchAuditLogs(keyword);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    // Keep the old endpoint for backward compatibility
    @GetMapping("/get-audit-logs/{date}")
    @Deprecated
    ResponseEntity<?> getAuditLogs(
            @PathVariable("date") @DateTimeFormat(pattern = "yyyy-MM-dd") Date date) {
        var response = auditService.getAuditClass(date);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
