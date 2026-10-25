package com.EduePoa.EP.academics.controller;

import com.EduePoa.EP.Reports.ReportCardRequest;
import com.EduePoa.EP.Reports.ReportsService;
import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.dto.request.AttendanceRequestDTO;
import com.EduePoa.EP.academics.dto.request.SaveScoresRequest;
import com.EduePoa.EP.academics.dto.request.SubmitAssessmentRequest;
import com.EduePoa.EP.academics.service.TeacherAssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Teacher-scoped assessment endpoints under {@code /api/v1/academics/assessments}. Every endpoint
 * resolves the teacher from the JWT-authenticated security context and enforces ownership in the
 * service layer (subject assignment or class-teacher), in addition to the permission check here.
 * Having a permission does not grant access to every grade/stream — ownership is always verified.
 */
@RestController
@RequestMapping("api/v1/academics/assessments")
@RequiredArgsConstructor
public class TeacherAssessmentController {

    private final TeacherAssessmentService teacherAssessmentService;
    private final ReportsService reportsService;

    /** The authenticated teacher's own subject assignments. */
    @GetMapping("/my-assignments")
    @PreAuthorize("hasPermission(null, 'exam:grade') or hasPermission(null, 'exam_mark:enter') or hasPermission(null, 'cat_mark:enter')")
    public ResponseEntity<?> myAssignments(@RequestParam(required = false) Integer year) {
        var res = teacherAssessmentService.getMyAssignments(year);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    /** Learner roster + existing marks for an owned assignment / exam type / term / year. */
    @GetMapping("/{assignmentId}/roster")
    @PreAuthorize("hasPermission(null, 'exam:grade') or hasPermission(null, 'exam_mark:enter') or hasPermission(null, 'cat_mark:enter')")
    public ResponseEntity<?> roster(@PathVariable Long assignmentId,
                                    @RequestParam(required = false) Long examTypeId,
                                    @RequestParam(required = false) Long termId,
                                    @RequestParam(required = false) Integer year) {
        var res = teacherAssessmentService.getRoster(assignmentId, examTypeId, termId, year);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    /** Idempotent save (upsert) of marks for an owned assignment. */
    @PostMapping("/{assignmentId}/scores")
    @PreAuthorize("hasPermission(null, 'exam:grade') or hasPermission(null, 'exam_mark:enter') or hasPermission(null, 'cat_mark:enter')")
    public ResponseEntity<?> saveScores(@PathVariable Long assignmentId,
                                        @RequestBody SaveScoresRequest request) {
        var res = teacherAssessmentService.saveScores(assignmentId, request);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    /** Submit an owned assessment for approval. */
    @PostMapping("/{assignmentId}/submit")
    @PreAuthorize("hasPermission(null, 'exam:grade') or hasPermission(null, 'exam_mark:enter')")
    public ResponseEntity<?> submit(@PathVariable Long assignmentId,
                                    @RequestBody SubmitAssessmentRequest request) {
        var res = teacherAssessmentService.submit(assignmentId, request);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    /** Classes the authenticated teacher is the class teacher of. */
    @GetMapping("/my-class")
    @PreAuthorize("hasPermission(null, 'attendance:read') or hasPermission(null, 'report:generate') or hasPermission(null, 'exam:grade')")
    public ResponseEntity<?> myClass() {
        var res = teacherAssessmentService.getMyClass();
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    /** Compute CBC results for an owned class (grade-wide; stream scope is validated for ownership). */
    @PostMapping("/{gradeId}/compute")
    @PreAuthorize("hasPermission(null, 'exam:grade') or hasPermission(null, 'grade:publish')")
    public ResponseEntity<?> compute(@PathVariable Long gradeId,
                                     @RequestParam(required = false) Long streamId,
                                     @RequestParam(required = false) Long termId,
                                     @RequestParam(required = false) Integer year) {
        var res = teacherAssessmentService.computeResults(gradeId, streamId, termId, year);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    /** All exam results for one learner across the year, grouped by term. Ownership enforced. */
    @GetMapping("/students/{studentId}/results")
    @PreAuthorize("hasPermission(null, 'report:generate') or hasPermission(null, 'exam:read') or hasPermission(null, 'exam:grade')")
    public ResponseEntity<?> studentResults(@PathVariable Long studentId,
                                            @RequestParam(required = false) Integer year) {
        var res = teacherAssessmentService.getStudentResults(studentId, year);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    /** Class-performance table for an owned class. */
    @GetMapping("/{gradeId}/performance")
    @PreAuthorize("hasPermission(null, 'report:generate') or hasPermission(null, 'exam:read') or hasPermission(null, 'exam:grade')")
    public ResponseEntity<?> performance(@PathVariable Long gradeId,
                                         @RequestParam(required = false) Long streamId,
                                         @RequestParam(required = false) Long termId,
                                         @RequestParam(required = false) Integer year) {
        var res = teacherAssessmentService.getClassPerformance(gradeId, streamId, termId, year);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    // ------------------------------------------------------------------
    // Attendance (teacher-scoped; server enforces class-teacher ownership)
    // ------------------------------------------------------------------

    /** Attendance roster for an owned grade on a date (defaults to today). */
    @GetMapping("/attendance/class/{gradeId}")
    @PreAuthorize("hasPermission(null, 'attendance:read')")
    public ResponseEntity<?> attendanceRoster(
            @PathVariable Long gradeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        var res = teacherAssessmentService.getClassAttendanceRoster(gradeId, date);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    /** Mark/upsert attendance for an owned grade. Ownership is enforced server-side. */
    @PostMapping("/attendance")
    @PreAuthorize("hasPermission(null, 'attendance:mark')")
    public ResponseEntity<?> markAttendance(@RequestBody AttendanceRequestDTO request) {
        var res = teacherAssessmentService.markClassAttendance(request);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    /**
     * Per-learner attendance history for an owned class over a term (termId+year) or a date range
     * (from/to). Ownership enforced; scoped to the teacher's own stream/grade.
     */
    @GetMapping("/attendance/summary/class/{gradeId}")
    @PreAuthorize("hasPermission(null, 'attendance:read')")
    public ResponseEntity<?> attendanceSummary(
            @PathVariable Long gradeId,
            @RequestParam(required = false) Long streamId,
            @RequestParam(required = false) Long termId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var res = teacherAssessmentService.getClassAttendanceSummary(gradeId, streamId, termId, year, from, to);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    // ------------------------------------------------------------------
    // Report forms (teacher-scoped; ownership enforced before PDF generation)
    // ------------------------------------------------------------------

    /**
     * Generate/download a learner's CBC report-card PDF, but only when the authenticated teacher is
     * the class teacher of that learner. Reuses the existing {@link ReportsService} PDF generation.
     */
    @PostMapping("/report-card")
    @PreAuthorize("hasPermission(null, 'report:generate')")
    public ResponseEntity<?> reportCard(@RequestBody ReportCardRequest request) {
        if (request == null || request.getStudentId() == null) {
            CustomResponse<String> bad = new CustomResponse<>();
            bad.setStatusCode(HttpStatus.BAD_REQUEST.value());
            bad.setMessage("studentId is required");
            return ResponseEntity.status(bad.getStatusCode()).body(bad);
        }
        // Ownership gate: the teacher must be class teacher of the learner's class.
        CustomResponse<Long> auth = teacherAssessmentService.authorizeReportAccess(request.getStudentId());
        if (auth.getStatusCode() == null || auth.getStatusCode() != HttpStatus.OK.value()) {
            return ResponseEntity.status(auth.getStatusCode() == null ? 403 : auth.getStatusCode()).body(auth);
        }
        // Backfill gradeId from the authorized learner when the caller omitted it.
        if (request.getGradeId() == null) {
            request.setGradeId(auth.getEntity());
        }

        CustomResponse<?> res = reportsService.generateCbcReportCard(request);
        if (res.getStatusCode() != null && res.getStatusCode() == HttpStatus.OK.value()) {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=cbc-report-card-" + request.getStudentId() + "-" + request.getYear() + ".pdf");
            return ResponseEntity.status(res.getStatusCode())
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(res.getEntity());
        }
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }
}
