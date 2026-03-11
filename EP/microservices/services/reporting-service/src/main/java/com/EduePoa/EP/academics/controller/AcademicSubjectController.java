package com.EduePoa.EP.academics.controller;


import com.EduePoa.EP.academics.service.AcademicSubjectService;
import com.EduePoa.EP.academics.dto.request.CreateSubjectRequest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/academics/subjects")
@RequiredArgsConstructor
public class AcademicSubjectController {

    private final AcademicSubjectService subjectService;

    @PostMapping("/create")
    public ResponseEntity<?> createSubject(@RequestBody CreateSubjectRequest request) {
        var res = subjectService.createSubject(request);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @GetMapping("/get-all")
    public ResponseEntity<?> getAllSubjects() {
        var res = subjectService.getAllSubjects();
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @GetMapping("/grade/{gradeId}")
    public ResponseEntity<?> getSubjectsByGrade(@PathVariable Long gradeId) {
        var res = subjectService.getSubjectsByGrade(gradeId);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @DeleteMapping("/{subjectId}")
    public ResponseEntity<?> deleteSubject(@PathVariable Long subjectId) {
        var res = subjectService.deleteSubject(subjectId);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @GetMapping("/get-subject-per-student/{studentId}")
    public ResponseEntity<?> getSubjectsByStudent(@PathVariable Long studentId) {
        var res = subjectService.getSubjectsByStudent(studentId);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

}
