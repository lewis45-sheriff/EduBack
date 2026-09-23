package com.EduePoa.EP.academics.controller;


import com.EduePoa.EP.academics.service.AcademicSubjectService;
import com.EduePoa.EP.academics.dto.request.CreateSubjectRequest;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * @deprecated These legacy "subject" endpoints are superseded by the CBC learning-area catalogue.
 * Use {@code GET /api/v1/academics/curriculum/learning-areas} (and the other
 * {@code /api/v1/academics/curriculum/**} endpoints) as the source of truth for subjects. Each
 * active learning area is bridged to an operational {@code AcademicSubject} (see its
 * {@code academicSubjectId}), so callers that still need a subject id can obtain it from a learning
 * area. These endpoints remain functional but emit a {@code Deprecation} header and will be removed
 * in a future release after a dependency review.
 */
@Deprecated
@RestController
@RequestMapping("api/v1/academics/subjects")
@RequiredArgsConstructor
public class AcademicSubjectController {

    private static final String SUCCESSOR = "/api/v1/academics/curriculum/learning-areas";

    private final AcademicSubjectService subjectService;

    /** Adds RFC-8594-style deprecation signals pointing callers to the learning-area successor. */
    private HttpHeaders deprecationHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Deprecation", "true");
        headers.add("Link", "<" + SUCCESSOR + ">; rel=\"successor-version\"");
        return headers;
    }

    @Deprecated
    @Operation(deprecated = true, summary = "[Deprecated] Use CBC learning areas instead")
    @PostMapping("/create")
    public ResponseEntity<?> createSubject(@RequestBody CreateSubjectRequest request) {
        var res = subjectService.createSubject(request);
        return ResponseEntity.status(res.getStatusCode()).headers(deprecationHeaders()).body(res);
    }

    @Deprecated
    @Operation(deprecated = true, summary = "[Deprecated] Use GET /api/v1/academics/curriculum/learning-areas")
    @GetMapping("/get-all")
    public ResponseEntity<?> getAllSubjects() {
        var res = subjectService.getAllSubjects();
        return ResponseEntity.status(res.getStatusCode()).headers(deprecationHeaders()).body(res);
    }

    @Deprecated
    @Operation(deprecated = true, summary = "[Deprecated] Use GET /api/v1/academics/curriculum/grades/{id}/learning-areas")
    @GetMapping("/grade/{gradeId}")
    public ResponseEntity<?> getSubjectsByGrade(@PathVariable Long gradeId) {
        var res = subjectService.getSubjectsByGrade(gradeId);
        return ResponseEntity.status(res.getStatusCode()).headers(deprecationHeaders()).body(res);
    }

    @Deprecated
    @Operation(deprecated = true, summary = "[Deprecated] Use CBC learning areas instead")
    @DeleteMapping("/{subjectId}")
    public ResponseEntity<?> deleteSubject(@PathVariable Long subjectId) {
        var res = subjectService.deleteSubject(subjectId);
        return ResponseEntity.status(res.getStatusCode()).headers(deprecationHeaders()).body(res);
    }

    @Deprecated
    @Operation(deprecated = true, summary = "[Deprecated] Use CBC learning areas instead")
    @GetMapping("/get-subject-per-student/{studentId}")
    public ResponseEntity<?> getSubjectsByStudent(@PathVariable Long studentId) {
        var res = subjectService.getSubjectsByStudent(studentId);
        return ResponseEntity.status(res.getStatusCode()).headers(deprecationHeaders()).body(res);
    }

}
