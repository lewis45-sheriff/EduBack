package com.EduePoa.EP.academics.controller;

import com.EduePoa.EP.academics.dto.request.AssignSubjectsToGradeRequest;
import com.EduePoa.EP.academics.service.ClassSubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/academics/classes")
@RequiredArgsConstructor
public class ClassSubjectController {

    private final ClassSubjectService classSubjectService;

    @PostMapping("/assign")
    public ResponseEntity<?> assignSubjectsToGrade(@RequestBody AssignSubjectsToGradeRequest request) {
        var res = classSubjectService.assignSubjectsToGrade(request);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @GetMapping("/{gradeId}/subjects")
    public ResponseEntity<?> getAssignedSubjects(@PathVariable Long gradeId, @RequestParam int year) {
        var res = classSubjectService.getAssignedSubjects(gradeId, year);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }
}
