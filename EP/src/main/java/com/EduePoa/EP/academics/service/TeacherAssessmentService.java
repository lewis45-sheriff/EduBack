package com.EduePoa.EP.academics.service;

import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.dto.request.AttendanceRequestDTO;
import com.EduePoa.EP.academics.dto.request.SaveScoresRequest;
import com.EduePoa.EP.academics.dto.request.SubmitAssessmentRequest;
import com.EduePoa.EP.academics.dto.response.AssignmentRosterDto;
import com.EduePoa.EP.academics.dto.response.CbcResultDto;
import com.EduePoa.EP.academics.dto.response.ClassAttendanceDto;
import com.EduePoa.EP.academics.dto.response.ClassPerformanceRowDto;
import com.EduePoa.EP.academics.dto.response.SaveScoresResultDto;
import com.EduePoa.EP.academics.dto.response.SubmitAssessmentResultDto;
import com.EduePoa.EP.academics.dto.response.TeacherAssignmentDto;
import com.EduePoa.EP.academics.dto.response.TeacherClassDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Teacher-scoped assessment API. Every method resolves the teacher from the authenticated security
 * context (never from the request) and enforces ownership (subject assignment or class-teacher)
 * before delegating to the existing admin-side business logic. It does not re-implement marks,
 * attendance, CBC computation, reporting or ranking — it authorizes and adapts them for teachers.
 */
public interface TeacherAssessmentService {

    /** The authenticated teacher's own subject assignments for a year (defaults to current year). */
    CustomResponse<List<TeacherAssignmentDto>> getMyAssignments(Integer year);

    /** Roster (learners + existing marks) for an owned assignment, exam type, term and year. */
    CustomResponse<AssignmentRosterDto> getRoster(Long assignmentId, Long examTypeId, Long termId, Integer year);

    /** Idempotent insert-or-update of marks for an owned assignment. */
    CustomResponse<SaveScoresResultDto> saveScores(Long assignmentId, SaveScoresRequest request);

    /** Submit an owned assessment for approval (DRAFT/AMENDED -&gt; SUBMITTED). */
    CustomResponse<SubmitAssessmentResultDto> submit(Long assignmentId, SubmitAssessmentRequest request);

    /** Classes (grade/stream) the authenticated teacher is the class teacher of. */
    CustomResponse<List<TeacherClassDto>> getMyClass();

    /** Compute CBC results for an owned class (authorizes then delegates to the existing engine). */
    CustomResponse<List<CbcResultDto>> computeResults(Long gradeId, Long streamId, Long termId, Integer year);

    /** Class-performance table for an owned class, built from existing computed summaries + ranking. */
    CustomResponse<List<ClassPerformanceRowDto>> getClassPerformance(Long gradeId, Long streamId, Long termId, Integer year);

    /**
     * Authorize that the authenticated teacher is the class teacher of the class the given learner
     * belongs to, for report-form access. Returns the learner's gradeId in the entity on success,
     * 403 otherwise. Used by the report controller before delegating to PDF generation.
     */
    CustomResponse<Long> authorizeReportAccess(Long studentId);

    /**
     * All of a learner's exam results for a year, grouped by term. Enforces class-teacher ownership
     * and reuses the existing report-card assembly for each term (no new result computation).
     */
    CustomResponse<com.EduePoa.EP.academics.dto.response.StudentResultsDto> getStudentResults(
            Long studentId, Integer year);

    /** Attendance roster for an owned grade on a date (delegates to the existing attendance service). */
    CustomResponse<ClassAttendanceDto> getClassAttendanceRoster(Long gradeId, LocalDate date);

    /** Mark/upsert attendance for an owned grade (ownership enforced before delegating). */
    CustomResponse<String> markClassAttendance(AttendanceRequestDTO request);

    /**
     * Per-learner attendance tallies for an owned class over a date range or a term. When
     * {@code termId}+{@code year} are given the term's date window is used; otherwise {@code from}/
     * {@code to} apply. Optionally narrowed to a single {@code streamId} the teacher owns; when null
     * the result covers all the teacher's owned stream(s)/grade.
     */
    CustomResponse<java.util.List<com.EduePoa.EP.academics.dto.response.AttendanceSummaryDto>>
            getClassAttendanceSummary(Long gradeId, Long streamId, Long termId, Integer year,
                                      LocalDate from, LocalDate to);
}
