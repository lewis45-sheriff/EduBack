package com.EduePoa.EP.academics.assessment.controller;

import com.EduePoa.EP.academics.assessment.dto.RecordCompetencyEvidenceRequest;
import com.EduePoa.EP.academics.assessment.service.CompetencyAssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Teacher-facing API for recording CBC competency evidence (the write path for the report card's
 * Core Competencies section). Secured with the {@code competency:assess} permission.
 */
@RestController
@RequestMapping("api/v1/academics/competencies")
@RequiredArgsConstructor
public class CompetencyAssessmentController {

    private final CompetencyAssessmentService competencyAssessmentService;

    /** Record one or more competency observations for a learner in a term. */
    @PostMapping("/evidence")
    @PreAuthorize("hasPermission(null, 'competency:assess')")
    public ResponseEntity<?> recordEvidence(@RequestBody RecordCompetencyEvidenceRequest request) {
        var res = competencyAssessmentService.recordEvidence(request);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    /**
     * List recorded competency evidence, filtered by grade / term / year (and optionally a single
     * learner). Backs the admin "Competency Evidence" screen, which scopes the table by grade.
     */
    @GetMapping("/evidence")
    @PreAuthorize("hasPermission(null, 'competency:assess') or hasPermission(null, 'report:generate')")
    public ResponseEntity<?> listEvidence(@RequestParam(required = false) Long gradeId,
                                          @RequestParam(required = false) Long termId,
                                          @RequestParam(required = false) Integer year,
                                          @RequestParam(required = false) Long studentId) {
        var res = competencyAssessmentService.listEvidence(gradeId, termId, year, studentId);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    /** List a learner's recorded competency evidence for a term/year. */
    @GetMapping("/evidence/student/{studentId}")
    @PreAuthorize("hasPermission(null, 'competency:assess') or hasPermission(null, 'report:generate')")
    public ResponseEntity<?> getEvidence(@PathVariable Long studentId,
                                         @RequestParam Long termId,
                                         @RequestParam int year) {
        var res = competencyAssessmentService.getEvidenceForLearner(studentId, termId, year);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    /**
     * List the performance levels (code/label/points) of the framework that applies to a learner,
     * so the record-evidence UI can present valid levels in a dropdown instead of hard-coding them.
     */
    @GetMapping("/evidence/levels/student/{studentId}")
    @PreAuthorize("hasPermission(null, 'competency:assess') or hasPermission(null, 'report:generate')")
    public ResponseEntity<?> getPerformanceLevels(@PathVariable Long studentId) {
        var res = competencyAssessmentService.getPerformanceLevelsForLearner(studentId);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }
}
