package com.EduePoa.EP.academics.curriculum.controller;

import com.EduePoa.EP.academics.curriculum.service.CurriculumService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Read APIs for the CBC curriculum catalogue (sections 51–52). All endpoints are secured with the
 * {@code curriculum:read} permission and return the standard {@code CustomResponse<T>}.
 */
@RestController
@RequestMapping("api/v1/academics/curriculum")
@RequiredArgsConstructor
public class CurriculumController {

    private final CurriculumService curriculumService;

    @GetMapping("/versions")
    @PreAuthorize("hasPermission(null, 'curriculum:read')")
    public ResponseEntity<?> getVersions() {
        var res = curriculumService.getVersions();
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @GetMapping("/grades")
    @PreAuthorize("hasPermission(null, 'curriculum:read')")
    public ResponseEntity<?> getGrades() {
        var res = curriculumService.getGrades();
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @GetMapping("/learning-areas")
    @PreAuthorize("hasPermission(null, 'curriculum:read')")
    public ResponseEntity<?> getAllLearningAreas() {
        var res = curriculumService.getAllLearningAreas();
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @GetMapping("/grades/{gradeId}/learning-areas")
    @PreAuthorize("hasPermission(null, 'curriculum:read')")
    public ResponseEntity<?> getLearningAreasForGrade(@PathVariable Long gradeId) {
        var res = curriculumService.getLearningAreasForGrade(gradeId);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @GetMapping("/learning-areas/{id}")
    @PreAuthorize("hasPermission(null, 'curriculum:read')")
    public ResponseEntity<?> getLearningArea(@PathVariable Long id) {
        var res = curriculumService.getLearningArea(id);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @GetMapping("/learning-areas/{id}/strands")
    @PreAuthorize("hasPermission(null, 'curriculum:read')")
    public ResponseEntity<?> getStrands(@PathVariable Long id) {
        var res = curriculumService.getStrands(id);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @GetMapping("/strands/{id}/sub-strands")
    @PreAuthorize("hasPermission(null, 'curriculum:read')")
    public ResponseEntity<?> getSubStrands(@PathVariable Long id) {
        var res = curriculumService.getSubStrands(id);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @GetMapping("/sub-strands/{id}/learning-outcomes")
    @PreAuthorize("hasPermission(null, 'curriculum:read')")
    public ResponseEntity<?> getLearningOutcomes(@PathVariable Long id) {
        var res = curriculumService.getLearningOutcomes(id);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @GetMapping("/competencies")
    @PreAuthorize("hasPermission(null, 'curriculum:read')")
    public ResponseEntity<?> getCompetencies() {
        var res = curriculumService.getCompetencies();
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @GetMapping("/values")
    @PreAuthorize("hasPermission(null, 'curriculum:read')")
    public ResponseEntity<?> getValues() {
        var res = curriculumService.getValues();
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @GetMapping("/pcis")
    @PreAuthorize("hasPermission(null, 'curriculum:read')")
    public ResponseEntity<?> getPcis() {
        var res = curriculumService.getPcis();
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }
}
