package com.EduePoa.EP.academics.controller;

import com.EduePoa.EP.academics.service.CbcCalculatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/academics/cbc")
@RequiredArgsConstructor
public class CbcCalculatorController {

    private final CbcCalculatorService cbcCalculatorService;

    @PostMapping("/compute")
    public ResponseEntity<?> computeForGrade(@RequestParam Long gradeId, @RequestParam Long termId, @RequestParam int year) {
        var res = cbcCalculatorService.computeForGrade(gradeId, termId, year);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }
    @GetMapping("/student/{studentId}/term/{termId}")
    public ResponseEntity<?> getResultsForStudent(@PathVariable Long studentId, @PathVariable Long termId) {
        var res = cbcCalculatorService.getResultsForStudent(studentId, termId);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }
}
