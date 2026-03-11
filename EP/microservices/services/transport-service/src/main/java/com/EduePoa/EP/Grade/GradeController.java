package com.EduePoa.EP.Grade;

import com.EduePoa.EP.Grade.Requests.GradeCreateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/grade/")
@RequiredArgsConstructor
public class GradeController {
    private  final  GradeService gradeService;

    @PostMapping("/create")
    public ResponseEntity<?> createGrade(@Valid @RequestBody GradeCreateRequest gradeCreateRequest) {
        var response = gradeService.createGrade(gradeCreateRequest.getGrade(), gradeCreateRequest.getStartRange(), gradeCreateRequest.getEndRange());
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("/all")
    public ResponseEntity<?> getAllGrades() {
        var response = gradeService.getAllGrades();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @PatchMapping("/delete/{id}")
    public ResponseEntity<?> delete(Long id) {
        var response = gradeService.delete(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }





}
