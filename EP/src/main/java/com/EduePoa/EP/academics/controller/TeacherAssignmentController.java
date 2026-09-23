package com.EduePoa.EP.academics.controller;

import com.EduePoa.EP.academics.dto.request.AssignTeacherToSubjectRequest;
import com.EduePoa.EP.academics.dto.request.BulkAssignTeacherRequest;
import com.EduePoa.EP.academics.service.TeacherAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Teacher-to-subject assignment endpoints. Assignment/removal require the existing
 * {@code class:update} permission ("Assign teachers to classes"); reads use {@code class:read}.
 */
@RestController
@RequestMapping("api/v1/academics/teacher-assignments")
@RequiredArgsConstructor
public class TeacherAssignmentController {

    private final TeacherAssignmentService teacherAssignmentService;

    @PostMapping("/assign")
    @PreAuthorize("hasPermission(null, 'class:update')")
    public ResponseEntity<?> assign(@RequestBody AssignTeacherToSubjectRequest request) {
        var res = teacherAssignmentService.assignTeacher(request);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    /** Assign one teacher to many subjects/classes at once (a teacher can teach across classes). */
    @PostMapping("/assign-bulk")
    @PreAuthorize("hasPermission(null, 'class:update')")
    public ResponseEntity<?> assignBulk(@RequestBody BulkAssignTeacherRequest request) {
        var res = teacherAssignmentService.bulkAssign(request);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @DeleteMapping("/{assignmentId}")
    @PreAuthorize("hasPermission(null, 'class:update')")
    public ResponseEntity<?> unassign(@PathVariable Long assignmentId) {
        var res = teacherAssignmentService.unassignTeacher(assignmentId);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @GetMapping("/teacher/{teacherId}")
    @PreAuthorize("hasPermission(null, 'class:read')")
    public ResponseEntity<?> byTeacher(@PathVariable Long teacherId, @RequestParam int year) {
        var res = teacherAssignmentService.getByTeacher(teacherId, year);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @GetMapping("/grade/{gradeId}")
    @PreAuthorize("hasPermission(null, 'class:read')")
    public ResponseEntity<?> byGrade(@PathVariable Long gradeId, @RequestParam int year) {
        var res = teacherAssignmentService.getByGrade(gradeId, year);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @GetMapping("/subject/{subjectId}")
    @PreAuthorize("hasPermission(null, 'class:read')")
    public ResponseEntity<?> bySubject(@PathVariable Long subjectId, @RequestParam int year) {
        var res = teacherAssignmentService.getBySubject(subjectId, year);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }
}
