package com.EduePoa.EP.academics.assessment.controller;

import com.EduePoa.EP.academics.assessment.dto.RecordTeacherRemarkRequest;
import com.EduePoa.EP.academics.assessment.service.TeacherRemarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Teacher-facing API for the class teacher's report-card remark (the editable comment shown in the
 * "Class Teacher's Remarks" section of the CBC report card). Secured with {@code competency:assess}
 * for writes, mirroring the competency-evidence write path; reads also allow {@code report:generate}.
 */
@RestController
@RequestMapping("api/v1/academics/report-remarks")
@RequiredArgsConstructor
public class TeacherRemarkController {

    private final TeacherRemarkService teacherRemarkService;

    /** Record (or clear, when the remark is blank) a learner's class-teacher remark for a term. */
    @PostMapping
    @PreAuthorize("hasPermission(null, 'competency:assess')")
    public ResponseEntity<?> recordRemark(@RequestBody RecordTeacherRemarkRequest request) {
        var res = teacherRemarkService.recordRemark(request);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    /** Read a learner's current class-teacher remark for a term/year. */
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasPermission(null, 'competency:assess') or hasPermission(null, 'report:generate')")
    public ResponseEntity<?> getRemark(@PathVariable Long studentId,
                                       @RequestParam Long termId,
                                       @RequestParam int year) {
        var res = teacherRemarkService.getRemark(studentId, termId, year);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }
}
