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
    @GetMapping("/get-audit-logs/{date}")
    ResponseEntity<?>getAuditLogs(@PathVariable("date")
                                  @DateTimeFormat(pattern = "yyyy-MM-dd")
                                  Date date){
        var response = auditService.getAuditClass(date);
        return ResponseEntity.status(response.getStatusCode()).body(response);

    }
}

