package com.EduePoa.EP.academics.controller;

import com.EduePoa.EP.academics.service.ReportCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/academics/reports")
@RequiredArgsConstructor
public class ReportCardController {

    private final ReportCardService reportCardService;

    @GetMapping("/report-card/student/{studentId}")
    @PreAuthorize("hasPermission(null, 'report:generate')")
    public ResponseEntity<?> getReportCard(@PathVariable Long studentId,
                                           @RequestParam Long termId,
                                           @RequestParam int year) {
        var res = reportCardService.getReportCard(studentId, termId, year);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    /**
     * Lists learners eligible for a report card for a grade (optionally a stream), term and year.
     * Backs the admin report-card search screen. Each item flags whether results are ready.
     */
    @GetMapping("/report-card/list")
    @PreAuthorize("hasPermission(null, 'report:generate')")
    public ResponseEntity<?> listReportCards(@RequestParam Long gradeId,
                                             @RequestParam(required = false) Long streamId,
                                             @RequestParam Long termId,
                                             @RequestParam int year) {
        var res = reportCardService.listReportCards(gradeId, streamId, termId, year);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }
}
