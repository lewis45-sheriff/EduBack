package com.EduePoa.EP.Grade.Stream;

import com.EduePoa.EP.Grade.Stream.Requests.GradeStreamCreateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/grade/stream/")
@RequiredArgsConstructor
public class GradeStreamController {
    private final GradeStreamService gradeStreamService;

    @PostMapping("/create")
    public ResponseEntity<?> createStreams(@Valid @RequestBody GradeStreamCreateRequest request) {
        var response = gradeStreamService.createStreams(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/grade/{gradeId}")
    public ResponseEntity<?> getStreamsByGrade(@PathVariable Long gradeId) {
        var response = gradeStreamService.getStreamsByGrade(gradeId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllStreams() {
        var response = gradeStreamService.getAllStreams();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PatchMapping("/delete/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        var response = gradeStreamService.delete(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
