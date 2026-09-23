package com.EduePoa.EP.Grade.Stream;

import com.EduePoa.EP.Grade.Stream.Requests.AssignClassTeacherRequest;
import com.EduePoa.EP.Grade.Stream.Requests.GradeStreamCreateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PatchMapping("/{streamId}/class-teacher")
    @PreAuthorize("hasPermission(null, 'class:update')")
    public ResponseEntity<?> assignClassTeacher(@PathVariable Long streamId,
                                                 @Valid @RequestBody AssignClassTeacherRequest request) {
        var response = gradeStreamService.assignClassTeacher(streamId, request.getStaffId());
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping("/{streamId}/class-teacher")
    @PreAuthorize("hasPermission(null, 'class:update')")
    public ResponseEntity<?> removeClassTeacher(@PathVariable Long streamId) {
        var response = gradeStreamService.removeClassTeacher(streamId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
