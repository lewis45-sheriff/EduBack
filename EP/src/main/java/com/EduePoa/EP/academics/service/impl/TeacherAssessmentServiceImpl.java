package com.EduePoa.EP.academics.service.impl;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.Grade.Stream.GradeStream;
import com.EduePoa.EP.Grade.Stream.GradeStreamRepository;
import com.EduePoa.EP.Scoring.ExamType;
import com.EduePoa.EP.Scoring.ExamTypeRepository;
import com.EduePoa.EP.Scoring.StudentsScore;
import com.EduePoa.EP.Scoring.StudentsScoreRepository;
import com.EduePoa.EP.Staff.Staff;
import com.EduePoa.EP.Staff.StaffRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.assessment.enums.ResultStatus;
import com.EduePoa.EP.academics.assessment.entity.StudentTermSummary;
import com.EduePoa.EP.academics.assessment.repository.StudentTermSummaryRepository;
import com.EduePoa.EP.academics.assessment.service.RankingService;
import com.EduePoa.EP.academics.common.TermResolver;
import com.EduePoa.EP.academics.dto.request.SaveScoresRequest;
import com.EduePoa.EP.academics.dto.request.SubmitAssessmentRequest;
import com.EduePoa.EP.academics.dto.response.AssignmentRosterDto;
import com.EduePoa.EP.academics.dto.response.CbcResultDto;
import com.EduePoa.EP.academics.dto.response.ClassPerformanceRowDto;
import com.EduePoa.EP.academics.dto.response.SaveScoresResultDto;
import com.EduePoa.EP.academics.dto.response.SubmitAssessmentResultDto;
import com.EduePoa.EP.academics.dto.response.TeacherAssignmentDto;
import com.EduePoa.EP.academics.dto.response.TeacherClassDto;
import com.EduePoa.EP.academics.entity.AcademicSubject;
import com.EduePoa.EP.academics.entity.TeacherSubjectAssignment;
import com.EduePoa.EP.academics.repository.TeacherSubjectAssignmentRepository;
import com.EduePoa.EP.academics.service.CbcCalculatorService;
import com.EduePoa.EP.academics.service.TeacherAssessmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Teacher-scoped assessment service. Wraps the existing admin-side logic with authenticated-teacher
 * identity resolution, ownership validation and response mapping. It never trusts a client-supplied
 * teacher id, and all queries run under the existing Hibernate tenant filter.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeacherAssessmentServiceImpl implements TeacherAssessmentService {

    private final TeacherSubjectAssignmentRepository assignmentRepository;
    private final StudentsScoreRepository studentsScoreRepository;
    private final StudentRepository studentRepository;
    private final ExamTypeRepository examTypeRepository;
    private final GradeRepository gradeRepository;
    private final GradeStreamRepository gradeStreamRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final TermResolver termResolver;
    private final CbcCalculatorService cbcCalculatorService;
    private final StudentTermSummaryRepository studentTermSummaryRepository;
    private final RankingService rankingService;
    private final com.EduePoa.EP.academics.service.AttendanceService attendanceService;
    private final com.EduePoa.EP.academics.service.ReportCardService reportCardService;
    private final com.EduePoa.EP.academics.repository.CbcGradeResultRepository cbcGradeResultRepository;

    // ------------------------------------------------------------------
    // Identity + authorization helpers
    // ------------------------------------------------------------------

    /** Resolve the authenticated User from the security context (email-based), or null. */
    private User resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    /** Resolve the Staff record linked to the authenticated user (class-teacher identity), or null. */
    private Staff resolveCurrentStaff(User user) {
        if (user == null) return null;
        Staff staff = staffRepository.findByUserId(user.getId()).orElse(null);
        if (staff == null && user.getEmail() != null) {
            // Fallback: some tenants link staff and user only by a shared email.
            staff = staffRepository.findByEmail(user.getEmail()).orElse(null);
        }
        return staff;
    }

    private <T> CustomResponse<T> error(HttpStatus status, String message) {
        CustomResponse<T> r = new CustomResponse<>();
        r.setStatusCode(status.value());
        r.setMessage(message);
        return r;
    }

    private int effectiveYear(Integer year) {
        return year == null ? Year.now().getValue() : year;
    }

    // ------------------------------------------------------------------
    // 1. My assignments
    // ------------------------------------------------------------------

    @Override
    public CustomResponse<List<TeacherAssignmentDto>> getMyAssignments(Integer year) {
        User teacher = resolveCurrentUser();
        if (teacher == null) {
            return error(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        int resolvedYear = effectiveYear(year);
        List<TeacherSubjectAssignment> assignments =
                assignmentRepository.findByTeacherIdAndYear(teacher.getId(), Year.of(resolvedYear));

        List<TeacherAssignmentDto> dtos = new ArrayList<>();
        for (TeacherSubjectAssignment a : assignments) {
            if (a.getActive() != null && !a.getActive()) {
                continue; // skip deactivated assignments
            }
            dtos.add(toAssignmentDto(a, resolvedYear));
        }

        CustomResponse<List<TeacherAssignmentDto>> r = new CustomResponse<>();
        r.setStatusCode(HttpStatus.OK.value());
        r.setMessage(dtos.isEmpty() ? "No assignments found" : "Assignments retrieved");
        r.setEntity(dtos);
        return r;
    }

    private TeacherAssignmentDto toAssignmentDto(TeacherSubjectAssignment a, int year) {
        TeacherAssignmentDto dto = new TeacherAssignmentDto();
        dto.setAssignmentId(a.getId());
        if (a.getGrade() != null) {
            dto.setGradeId(a.getGrade().getId());
            dto.setGradeName(a.getGrade().getName());
        }
        if (a.getGradeStream() != null) {
            dto.setStreamId(a.getGradeStream().getId());
            dto.setStreamName(a.getGradeStream().getName());
        }
        if (a.getAcademicSubject() != null) {
            dto.setSubjectId(a.getAcademicSubject().getId());
            dto.setLearningAreaName(a.getAcademicSubject().getSubjectName());
        }
        dto.setYear(a.getYear() == null ? year : a.getYear().getValue());
        if (a.getTerm() != null) {
            dto.setTermId(termResolver.idOf(a.getTerm()));
            dto.setTermName(a.getTerm().name());
        }
        // Assessment status is per exam-type; at the assignment list level it starts as DRAFT.
        dto.setAssessmentStatus(ResultStatus.DRAFT.name());
        return dto;
    }

    // ------------------------------------------------------------------
    // 2. Roster
    // ------------------------------------------------------------------

    @Override
    public CustomResponse<AssignmentRosterDto> getRoster(Long assignmentId, Long examTypeId, Long termId, Integer year) {
        User teacher = resolveCurrentUser();
        if (teacher == null) {
            return error(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        TeacherSubjectAssignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
        if (assignment == null) {
            return error(HttpStatus.NOT_FOUND, "Assignment not found");
        }
        CustomResponse<AssignmentRosterDto> ownership = ownsAssignment(teacher, assignment);
        if (ownership != null) return ownership;

        Term term = resolveTermForAssignment(assignment, termId);
        if (term == null) {
            return error(HttpStatus.BAD_REQUEST, "Invalid or missing termId (use 1, 2 or 3), or it does not match the assignment term");
        }
        int resolvedYear = effectiveYear(year);

        ExamType examType = null;
        if (examTypeId != null) {
            examType = examTypeRepository.findById(examTypeId).orElse(null);
            if (examType == null) {
                return error(HttpStatus.NOT_FOUND, "Exam type not found");
            }
        }

        Grade grade = assignment.getGrade();
        GradeStream stream = assignment.getGradeStream();
        AcademicSubject subject = assignment.getAcademicSubject();

        List<Student> learners = loadLearners(grade, stream);

        // Existing scores for this subject/examType/term/year, indexed by studentId for O(1) lookup.
        Map<Long, StudentsScore> scoresByStudent = new LinkedHashMap<>();
        if (examType != null) {
            for (StudentsScore s : studentsScoreRepository
                    .findByGradeAndAcademicSubjectAndExamTypeAndTermAndYear(
                            grade, subject, examType, term, Year.of(resolvedYear))) {
                if (s.getStudent() != null) {
                    scoresByStudent.put(s.getStudent().getId(), s);
                }
            }
        }

        Integer maxScore = examType != null && examType.getMaxScore() != null
                ? examType.getMaxScore().intValue() : null;

        AssignmentRosterDto dto = new AssignmentRosterDto();
        dto.setAssignmentId(assignment.getId());
        dto.setSubjectId(subject == null ? null : subject.getId());
        dto.setLearningAreaName(subject == null ? null : subject.getSubjectName());
        dto.setGradeId(grade == null ? null : grade.getId());
        dto.setGradeName(grade == null ? null : grade.getName());
        dto.setStreamId(stream == null ? null : stream.getId());
        dto.setStreamName(stream == null ? null : stream.getName());
        dto.setTermId(termResolver.idOf(term));
        dto.setTermName(term.name());
        dto.setYear(resolvedYear);
        dto.setExamTypeId(examTypeId);
        dto.setExamTypeName(examType == null ? null : examType.getName());
        dto.setMaxScore(maxScore);

        String aggregateStatus = ResultStatus.DRAFT.name();
        for (Student student : learners) {
            AssignmentRosterDto.RosterLearner rl = new AssignmentRosterDto.RosterLearner();
            rl.setStudentId(student.getId());
            rl.setStudentName(buildStudentName(student));
            rl.setAdmissionNumber(student.getAdmissionNumber());
            rl.setMaxScore(maxScore);
            StudentsScore existing = scoresByStudent.get(student.getId());
            if (existing != null) {
                rl.setRawScore(existing.effectiveRawScore()); // may be null => blank, never 0
                rl.setStatus(existing.getResultStatus() == null
                        ? ResultStatus.DRAFT.name() : existing.getResultStatus().name());
                aggregateStatus = aggregateAssessmentStatus(aggregateStatus, rl.getStatus());
            } else {
                rl.setRawScore(null); // not entered -> blank
                rl.setStatus(ResultStatus.DRAFT.name());
            }
            dto.getLearners().add(rl);
        }
        dto.setAssessmentStatus(aggregateStatus);

        CustomResponse<AssignmentRosterDto> r = new CustomResponse<>();
        r.setStatusCode(HttpStatus.OK.value());
        r.setMessage("Roster retrieved");
        r.setEntity(dto);
        return r;
    }

    /** Non-DRAFT state wins so the UI locks a submitted/approved/published assessment. */
    private String aggregateAssessmentStatus(String current, String rowStatus) {
        if (rowStatus == null || ResultStatus.DRAFT.name().equals(rowStatus)) {
            return current;
        }
        // Prefer the most advanced status present on any row.
        ResultStatus c = safeStatus(current);
        ResultStatus s = safeStatus(rowStatus);
        return s.ordinal() > c.ordinal() ? s.name() : current;
    }

    private ResultStatus safeStatus(String name) {
        try {
            return ResultStatus.valueOf(name);
        } catch (Exception e) {
            return ResultStatus.DRAFT;
        }
    }

    // ------------------------------------------------------------------
    // 3. Save scores (upsert)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public CustomResponse<SaveScoresResultDto> saveScores(Long assignmentId, SaveScoresRequest request) {
        User teacher = resolveCurrentUser();
        if (teacher == null) {
            return error(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        if (request == null || request.getExamTypeId() == null || request.getTermId() == null) {
            return error(HttpStatus.BAD_REQUEST, "termId and examTypeId are required");
        }
        TeacherSubjectAssignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
        if (assignment == null) {
            return error(HttpStatus.NOT_FOUND, "Assignment not found");
        }
        CustomResponse<SaveScoresResultDto> ownership = ownsAssignment(teacher, assignment);
        if (ownership != null) return ownership;

        Term term = resolveTermForAssignment(assignment, request.getTermId());
        if (term == null) {
            return error(HttpStatus.BAD_REQUEST, "Invalid termId or it does not match the assignment term");
        }
        ExamType examType = examTypeRepository.findById(request.getExamTypeId()).orElse(null);
        if (examType == null) {
            return error(HttpStatus.NOT_FOUND, "Exam type not found");
        }
        int resolvedYear = effectiveYear(request.getYear());
        Year acaYear = Year.of(resolvedYear);

        Grade grade = assignment.getGrade();
        GradeStream stream = assignment.getGradeStream();
        AcademicSubject subject = assignment.getAcademicSubject();

        // Learners the teacher is allowed to score for this assignment (grade or stream scoped).
        List<Student> learners = loadLearners(grade, stream);
        Map<Long, Student> learnerById = new LinkedHashMap<>();
        for (Student s : learners) learnerById.put(s.getId(), s);

        BigDecimal max = examType.getMaxScore() == null ? null : BigDecimal.valueOf(examType.getMaxScore());
        BigDecimal min = examType.getMinScore() == null ? BigDecimal.ZERO : BigDecimal.valueOf(examType.getMinScore());

        SaveScoresResultDto result = new SaveScoresResultDto();

        List<SaveScoresRequest.ScoreEntry> entries =
                request.getScores() == null ? Collections.emptyList() : request.getScores();

        for (SaveScoresRequest.ScoreEntry entry : entries) {
            if (entry.getStudentId() == null) {
                result.getErrors().add("Missing studentId in a score entry");
                continue;
            }
            Student student = learnerById.get(entry.getStudentId());
            if (student == null) {
                // Not in the authorized roster -> reject, do not leak/alter other classes.
                result.getErrors().add("Student " + entry.getStudentId() + " is not in this assignment's class");
                continue;
            }

            BigDecimal rawScore = entry.getRawScore();
            if (rawScore == null) {
                // Blank = not entered. Never written as 0. We do not clear existing marks here.
                result.setSkippedBlank(result.getSkippedBlank() + 1);
                continue;
            }
            if (rawScore.signum() < 0 || rawScore.compareTo(min) < 0) {
                result.getErrors().add("Score for student " + entry.getStudentId() + " is below the minimum");
                continue;
            }
            if (max != null && rawScore.compareTo(max) > 0) {
                result.getErrors().add("Score " + rawScore + " for student " + entry.getStudentId()
                        + " exceeds the maximum " + max);
                continue;
            }

            Optional<StudentsScore> existingOpt = studentsScoreRepository
                    .findByStudentAndGradeAndTermAndAcademicSubjectAndExamTypeAndYear(
                            student, grade, term, subject, examType, acaYear);

            StudentsScore score = existingOpt.orElseGet(StudentsScore::new);
            boolean isNew = existingOpt.isEmpty();

            if (isNew) {
                score.setStudent(student);
                score.setGrade(grade);
                score.setTerm(term);
                score.setAcademicSubject(subject);
                score.setExamType(examType);
                score.setYear(acaYear);
                score.setResultStatus(ResultStatus.DRAFT);
            } else {
                // Do not silently edit a locked (submitted/approved/published) score.
                ResultStatus current = score.getResultStatus();
                if (current == ResultStatus.SUBMITTED || current == ResultStatus.APPROVED
                        || current == ResultStatus.PUBLISHED) {
                    result.getErrors().add("Score for student " + entry.getStudentId()
                            + " is " + current.name().toLowerCase() + " and cannot be edited");
                    continue;
                }
            }

            score.setRawScore(rawScore);
            score.setExamScore(rawScore.doubleValue()); // keep legacy field consistent for existing readers
            score.setMaximumScore(max);
            score.setSubjectTeacher(teacher); // authenticated identity, never client-supplied
            if (score.getResultStatus() == null) {
                score.setResultStatus(ResultStatus.DRAFT);
            }

            studentsScoreRepository.save(score);
            if (isNew) {
                result.setCreated(result.getCreated() + 1);
            } else {
                result.setUpdated(result.getUpdated() + 1);
            }
        }

        // If any validation error occurred and nothing could be saved, surface a 400.
        boolean nothingSaved = result.getCreated() == 0 && result.getUpdated() == 0;
        if (!result.getErrors().isEmpty() && nothingSaved) {
            CustomResponse<SaveScoresResultDto> r = new CustomResponse<>();
            r.setStatusCode(HttpStatus.BAD_REQUEST.value());
            r.setMessage("No valid scores were saved");
            r.setEntity(result);
            return r;
        }

        result.setAssessmentStatus(ResultStatus.DRAFT.name());
        CustomResponse<SaveScoresResultDto> r = new CustomResponse<>();
        r.setStatusCode(HttpStatus.OK.value());
        r.setMessage(String.format("Saved: %d created, %d updated, %d blank skipped",
                result.getCreated(), result.getUpdated(), result.getSkippedBlank()));
        r.setEntity(result);
        return r;
    }

    // ------------------------------------------------------------------
    // 4. Submit assessment
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public CustomResponse<SubmitAssessmentResultDto> submit(Long assignmentId, SubmitAssessmentRequest request) {
        User teacher = resolveCurrentUser();
        if (teacher == null) {
            return error(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        if (request == null || request.getExamTypeId() == null || request.getTermId() == null) {
            return error(HttpStatus.BAD_REQUEST, "termId and examTypeId are required");
        }
        TeacherSubjectAssignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
        if (assignment == null) {
            return error(HttpStatus.NOT_FOUND, "Assignment not found");
        }
        CustomResponse<SubmitAssessmentResultDto> ownership = ownsAssignment(teacher, assignment);
        if (ownership != null) return ownership;

        Term term = resolveTermForAssignment(assignment, request.getTermId());
        if (term == null) {
            return error(HttpStatus.BAD_REQUEST, "Invalid termId or it does not match the assignment term");
        }
        ExamType examType = examTypeRepository.findById(request.getExamTypeId()).orElse(null);
        if (examType == null) {
            return error(HttpStatus.NOT_FOUND, "Exam type not found");
        }
        int resolvedYear = effectiveYear(request.getYear());

        List<StudentsScore> scores = studentsScoreRepository
                .findByGradeAndAcademicSubjectAndExamTypeAndTermAndYear(
                        assignment.getGrade(), assignment.getAcademicSubject(), examType, term, Year.of(resolvedYear));

        if (scores.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "There are no saved marks to submit for this assessment");
        }

        int submitted = 0;
        for (StudentsScore s : scores) {
            ResultStatus current = s.getResultStatus();
            // Only DRAFT or AMENDED rows transition; already-submitted/approved/published are preserved.
            if (current == null || current == ResultStatus.DRAFT || current == ResultStatus.AMENDED) {
                s.setResultStatus(ResultStatus.SUBMITTED);
                studentsScoreRepository.save(s);
                submitted++;
            }
        }

        SubmitAssessmentResultDto dto = new SubmitAssessmentResultDto();
        dto.setAssessmentStatus(ResultStatus.SUBMITTED.name());
        dto.setSubmittedCount(submitted);

        CustomResponse<SubmitAssessmentResultDto> r = new CustomResponse<>();
        r.setStatusCode(HttpStatus.OK.value());
        r.setMessage(submitted == 0
                ? "Assessment already submitted"
                : "Assessment submitted for approval (" + submitted + " marks)");
        r.setEntity(dto);
        return r;
    }

    // ------------------------------------------------------------------
    // 5. My class (class-teacher)
    // ------------------------------------------------------------------

    @Override
    public CustomResponse<List<TeacherClassDto>> getMyClass() {
        User teacher = resolveCurrentUser();
        if (teacher == null) {
            return error(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        Staff staff = resolveCurrentStaff(teacher);
        List<TeacherClassDto> classes = new ArrayList<>();
        if (staff != null) {
            for (GradeStream stream : gradeStreamRepository.findAllByClassTeacherId(staff.getId())) {
                TeacherClassDto dto = new TeacherClassDto();
                Grade g = stream.getGrade();
                dto.setGradeId(g == null ? null : g.getId());
                dto.setGradeName(g == null ? null : g.getName());
                dto.setStreamId(stream.getId());
                dto.setStreamName(stream.getName());
                dto.setLearnerCount(loadLearners(g, stream).size());
                classes.add(dto);
            }
            for (Grade grade : gradeRepository.findAllByClassTeacherId(staff.getId())) {
                TeacherClassDto dto = new TeacherClassDto();
                dto.setGradeId(grade.getId());
                dto.setGradeName(grade.getName());
                dto.setLearnerCount(loadLearners(grade, null).size());
                classes.add(dto);
            }
        }

        CustomResponse<List<TeacherClassDto>> r = new CustomResponse<>();
        r.setStatusCode(HttpStatus.OK.value());
        r.setMessage(classes.isEmpty() ? "You are not assigned as a class teacher" : "Classes retrieved");
        r.setEntity(classes);
        return r;
    }

    // ------------------------------------------------------------------
    // 6. Compute results
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public CustomResponse<List<CbcResultDto>> computeResults(Long gradeId, Long streamId, Long termId, Integer year) {
        User teacher = resolveCurrentUser();
        if (teacher == null) {
            return error(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        CustomResponse<List<CbcResultDto>> ownership = ownsClass(teacher, gradeId, streamId);
        if (ownership != null) return ownership;

        Optional<Term> termOpt = termId == null ? Optional.empty() : termResolver.resolveById(termId);
        if (termOpt.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "Invalid or missing termId (use 1, 2 or 3)");
        }
        int resolvedYear = effectiveYear(year);

        // The existing CBC engine computes for the whole grade; streams are not computed in isolation.
        CustomResponse<List<CbcResultDto>> computed =
                cbcCalculatorService.computeForGrade(gradeId, termId, resolvedYear);

        boolean ok = computed != null && computed.getStatusCode() != null
                && computed.getStatusCode() >= 200 && computed.getStatusCode() < 300;

        if (ok) {
            // The CBC engine writes per-subject rows (cbc_grade_result). Aggregate them into the
            // per-term summary (cbc_student_term_summary) so class performance and ranking have an
            // overall figure to show. Without this the summary table stays empty and every learner
            // reads as "not computed".
            int summarised = buildTermSummaries(gradeId, termOpt.get(), Year.of(resolvedYear));
            String scopeNote = streamId != null
                    ? " (computation covers the whole grade, not only the selected stream)"
                    : "";
            computed.setMessage((computed.getMessage() == null ? "Results computed" : computed.getMessage())
                    + scopeNote + "; " + summarised + " learner summaries updated");
        }
        return computed;
    }

    /**
     * Aggregates the per-subject {@link com.EduePoa.EP.academics.entity.CbcGradeResult} rows for
     * every non-deleted learner in a grade into a per-term {@link StudentTermSummary} (overall
     * percentage = mean of the subjects' normalized scores; label = the band that mean falls into,
     * derived from the subjects' own labels). Idempotent upsert per student/term/year. Returns the
     * number of learners summarised. Position/ranking is applied separately on read by RankingService.
     */
    private int buildTermSummaries(Long gradeId, Term term, Year acaYear) {
        Grade grade = gradeRepository.findById(gradeId).orElse(null);
        if (grade == null) {
            return 0;
        }
        int count = 0;
        // Whole-grade scope matches the engine's scope; this covers every stream of the grade.
        for (Student student : loadLearners(grade, null)) {
            List<com.EduePoa.EP.academics.entity.CbcGradeResult> results =
                    cbcGradeResultRepository.findByStudentAndTermAndYear(student, term, acaYear);
            if (results.isEmpty()) {
                continue;
            }

            java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
            int n = 0;
            for (var r : results) {
                if (r.getNormalizedScore() != null) {
                    sum = sum.add(r.getNormalizedScore());
                    n++;
                }
            }
            java.math.BigDecimal overall = n > 0
                    ? sum.divide(java.math.BigDecimal.valueOf(n), 2, java.math.RoundingMode.HALF_UP)
                    : null;

            StudentTermSummary summary = studentTermSummaryRepository
                    .findByStudentAndTermAndYear(student, term, acaYear)
                    .orElseGet(StudentTermSummary::new);
            boolean isNew = summary.getId() == null;
            if (isNew) {
                summary.setStudent(student);
                summary.setTerm(term);
                summary.setYear(acaYear);
            }
            summary.setGrade(student.getGrade());
            summary.setGradeStream(student.getGradeStream());
            summary.setLearningAreasAssessed(results.size());
            summary.setOverallPercentage(overall);
            summary.setOverallPerformanceLabel(labelForPercentage(overall));
            studentTermSummaryRepository.save(summary);
            count++;
        }
        return count;
    }

    /**
     * Broad CBC band label for an aggregate percentage, matching the fallback bands the CBC engine
     * uses when no framework is configured. Kept consistent with the per-subject labels so the
     * overall reads sensibly alongside them. Returns null for a null percentage.
     */
    private String labelForPercentage(java.math.BigDecimal percentage) {
        if (percentage == null) {
            return null;
        }
        double p = percentage.doubleValue();
        if (p >= 76) return "Exceeding Expectation";
        if (p >= 51) return "Meeting Expectation";
        if (p >= 26) return "Approaching Expectation";
        return "Below Expectation";
    }

    // ------------------------------------------------------------------
    // 7. Class performance
    // ------------------------------------------------------------------

    @Override
    public CustomResponse<List<ClassPerformanceRowDto>> getClassPerformance(Long gradeId, Long streamId, Long termId, Integer year) {
        User teacher = resolveCurrentUser();
        if (teacher == null) {
            return error(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        CustomResponse<List<ClassPerformanceRowDto>> ownership = ownsClass(teacher, gradeId, streamId);
        if (ownership != null) return ownership;

        Optional<Term> termOpt = termId == null ? Optional.empty() : termResolver.resolveById(termId);
        if (termOpt.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "Invalid or missing termId (use 1, 2 or 3)");
        }
        Term term = termOpt.get();
        int resolvedYear = effectiveYear(year);
        Year acaYear = Year.of(resolvedYear);

        Grade grade = gradeRepository.findById(gradeId).orElse(null);
        if (grade == null) {
            return error(HttpStatus.NOT_FOUND, "Grade not found");
        }
        GradeStream stream = null;
        if (streamId != null) {
            stream = gradeStreamRepository.findById(streamId).orElse(null);
            if (stream == null) {
                return error(HttpStatus.NOT_FOUND, "Stream not found");
            }
        }

        List<Student> learners = loadLearners(grade, stream);

        // Existing computed summaries, indexed by student for reuse of the same aggregate + ranking.
        Map<Long, StudentTermSummary> summaryByStudent = new LinkedHashMap<>();
        List<StudentTermSummary> summaries = stream != null
                ? studentTermSummaryRepository.findByGradeStreamIdAndTermAndYear(streamId, term, acaYear)
                : studentTermSummaryRepository.findByGradeIdAndTermAndYear(gradeId, term, acaYear);
        for (StudentTermSummary s : summaries) {
            if (s.getStudent() != null) summaryByStudent.put(s.getStudent().getId(), s);
        }

        List<ClassPerformanceRowDto> rows = new ArrayList<>();
        for (Student student : learners) {
            ClassPerformanceRowDto row = new ClassPerformanceRowDto();
            row.setStudentId(student.getId());
            row.setStudentName(buildStudentName(student));
            row.setAdmissionNumber(student.getAdmissionNumber());

            StudentTermSummary summary = summaryByStudent.get(student.getId());
            if (summary != null) {
                row.setComputed(true);
                row.setOverallPercentage(summary.getOverallPercentage());
                row.setPerformanceLevel(summary.getOverallPerformanceLabel());
                // Reuse existing ranking logic rather than recomputing position.
                row.setPosition(summary.getPosition());
                if (row.getPosition() == null) {
                    rankingService.resolvePosition(student, term, acaYear).ifPresent(row::setPosition);
                }
                row.setRankingScope(rankingService.resolveScopeLabel(student, term, acaYear));
            } else {
                row.setComputed(false);
            }
            rows.add(row);
        }

        CustomResponse<List<ClassPerformanceRowDto>> r = new CustomResponse<>();
        r.setStatusCode(HttpStatus.OK.value());
        boolean anyComputed = rows.stream().anyMatch(ClassPerformanceRowDto::isComputed);
        r.setMessage(anyComputed ? "Class performance retrieved"
                : "No computed results yet for this class/term; compute results first");
        r.setEntity(rows);
        return r;
    }

    // ------------------------------------------------------------------
    // 8. Report access authorization
    // ------------------------------------------------------------------

    @Override
    public CustomResponse<Long> authorizeReportAccess(Long studentId) {
        User teacher = resolveCurrentUser();
        if (teacher == null) {
            return error(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        if (studentId == null) {
            return error(HttpStatus.BAD_REQUEST, "studentId is required");
        }
        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null || Boolean.TRUE.equals(student.getIsDeleted())) {
            return error(HttpStatus.NOT_FOUND, "Student not found");
        }
        Long gradeId = student.getGrade() == null ? null : student.getGrade().getId();
        Long streamId = student.getGradeStream() == null ? null : student.getGradeStream().getId();
        CustomResponse<Long> ownership = ownsClass(teacher, gradeId, streamId);
        if (ownership != null) {
            return error(HttpStatus.FORBIDDEN, "You are not the class teacher for this learner");
        }
        CustomResponse<Long> r = new CustomResponse<>();
        r.setStatusCode(HttpStatus.OK.value());
        r.setMessage("Authorized");
        r.setEntity(gradeId);
        return r;
    }

    // ------------------------------------------------------------------
    // 8b. Individual student results (all exams for the year, by term)
    // ------------------------------------------------------------------

    @Override
    public CustomResponse<com.EduePoa.EP.academics.dto.response.StudentResultsDto> getStudentResults(
            Long studentId, Integer year) {
        User teacher = resolveCurrentUser();
        if (teacher == null) {
            return error(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        if (studentId == null) {
            return error(HttpStatus.BAD_REQUEST, "studentId is required");
        }
        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null || Boolean.TRUE.equals(student.getIsDeleted())) {
            return error(HttpStatus.NOT_FOUND, "Student not found");
        }
        // Ownership: the teacher must be the class teacher of the learner's class.
        Long gradeId = student.getGrade() == null ? null : student.getGrade().getId();
        Long streamId = student.getGradeStream() == null ? null : student.getGradeStream().getId();
        CustomResponse<com.EduePoa.EP.academics.dto.response.StudentResultsDto> ownership =
                ownsClass(teacher, gradeId, streamId);
        if (ownership != null) return ownership;

        int resolvedYear = effectiveYear(year);

        com.EduePoa.EP.academics.dto.response.StudentResultsDto dto =
                new com.EduePoa.EP.academics.dto.response.StudentResultsDto();
        dto.setStudentId(student.getId());
        dto.setStudentName(buildStudentName(student));
        dto.setAdmissionNumber(student.getAdmissionNumber());
        dto.setGradeName(student.getGradeName());
        dto.setStreamName(student.getStreamName());
        dto.setYear(resolvedYear);

        // Reuse the existing report-card assembly for each term (1..3). No new result computation.
        for (long termId = 1; termId <= 3; termId++) {
            CustomResponse<com.EduePoa.EP.academics.dto.response.ReportCardDto> rc =
                    reportCardService.getReportCard(studentId, termId, resolvedYear);
            if (rc == null || rc.getStatusCode() == null
                    || rc.getStatusCode() != HttpStatus.OK.value() || rc.getEntity() == null) {
                continue;
            }
            com.EduePoa.EP.academics.dto.response.ReportCardDto card = rc.getEntity();

            com.EduePoa.EP.academics.dto.response.StudentResultsDto.TermResults tr =
                    new com.EduePoa.EP.academics.dto.response.StudentResultsDto.TermResults();
            tr.setTermId(termId);
            tr.setTermName(card.getTermName());
            tr.setOverallPercentage(card.getOverallPercentage());
            tr.setOverallPerformanceLabel(card.getOverallPerformanceLabel());
            tr.setPosition(card.getPosition());
            tr.setRankingScope(card.getRankingScope());
            tr.setLearningAreas(card.getLearningAreas());
            tr.setExams(card.getIndividualAssessments());

            boolean hasData = (card.getLearningAreas() != null && !card.getLearningAreas().isEmpty())
                    || (card.getIndividualAssessments() != null && !card.getIndividualAssessments().isEmpty());
            tr.setHasData(hasData);

            // Only include terms that actually have recorded data.
            if (hasData) {
                dto.getTerms().add(tr);
            }
        }

        // Performance trend across every computed term the learner has (all grades/years), so the
        // graph shows progression over time. Built from the already-computed StudentTermSummary rows.
        List<StudentTermSummary> history = studentTermSummaryRepository.findByStudent(student);
        history.stream()
                .sorted(java.util.Comparator
                        .comparing((StudentTermSummary s) -> s.getYear() == null ? 0 : s.getYear().getValue())
                        .thenComparing(s -> s.getTerm() == null ? 0 : termResolver.sequenceOf(s.getTerm())))
                .forEach(s -> {
                    com.EduePoa.EP.academics.dto.response.StudentResultsDto.TrendPoint tp =
                            new com.EduePoa.EP.academics.dto.response.StudentResultsDto.TrendPoint();
                    tp.setYear(s.getYear() == null ? null : s.getYear().getValue());
                    tp.setTermId(s.getTerm() == null ? null : termResolver.idOf(s.getTerm()));
                    tp.setTermName(s.getTerm() == null ? null : s.getTerm().name());
                    String gradeName = s.getGrade() != null ? s.getGrade().getName() : dto.getGradeName();
                    tp.setGradeName(gradeName);
                    String termShort = s.getTerm() == null ? "" : "Term " + termResolver.sequenceOf(s.getTerm());
                    tp.setLabel(((gradeName == null ? "" : gradeName + " · ") + termShort).trim());
                    tp.setOverallPercentage(s.getOverallPercentage());
                    tp.setOverallPerformanceLabel(s.getOverallPerformanceLabel());
                    tp.setPosition(s.getPosition());
                    dto.getTrend().add(tp);
                });

        CustomResponse<com.EduePoa.EP.academics.dto.response.StudentResultsDto> r = new CustomResponse<>();
        r.setStatusCode(HttpStatus.OK.value());
        r.setMessage(dto.getTerms().isEmpty()
                ? "No recorded results for this learner this year"
                : "Student results retrieved");
        r.setEntity(dto);
        return r;
    }

    // ------------------------------------------------------------------
    // 9. Attendance (teacher-scoped wrappers around the existing service)
    // ------------------------------------------------------------------

    @Override
    public CustomResponse<com.EduePoa.EP.academics.dto.response.ClassAttendanceDto> getClassAttendanceRoster(
            Long gradeId, java.time.LocalDate date) {
        User teacher = resolveCurrentUser();
        if (teacher == null) {
            return error(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        CustomResponse<com.EduePoa.EP.academics.dto.response.ClassAttendanceDto> ownership =
                ownsClass(teacher, gradeId, null);
        if (ownership != null) return ownership;

        // The underlying attendance service is grade-wide. Scope the roster to the EXACT class the
        // teacher is class teacher of: if they own a stream of this grade, show only that stream's
        // learners; if they own the whole (unstreamed) grade, show the grade's learners.
        java.util.Set<Long> allowedStudentIds = resolveOwnedLearnerIds(teacher, gradeId);

        var full = attendanceService.getClassAttendance(gradeId, date);
        if (full != null && full.getStudents() != null && allowedStudentIds != null) {
            java.util.List<com.EduePoa.EP.academics.dto.response.StudentAttendanceRowDto> filtered =
                    full.getStudents().stream()
                            .filter(row -> allowedStudentIds.contains(row.getStudentId()))
                            .collect(java.util.stream.Collectors.toList());
            int markedCount = (int) filtered.stream()
                    .filter(com.EduePoa.EP.academics.dto.response.StudentAttendanceRowDto::isMarked)
                    .count();
            full.setStudents(filtered);
            full.setTotalStudents(filtered.size());
            full.setMarkedCount(markedCount);
        }

        CustomResponse<com.EduePoa.EP.academics.dto.response.ClassAttendanceDto> r = new CustomResponse<>();
        r.setStatusCode(HttpStatus.OK.value());
        r.setMessage("Attendance roster retrieved");
        r.setEntity(full);
        return r;
    }

    /**
     * The learner ids the teacher is class teacher of within a grade. Prefers the teacher's own
     * stream(s) of that grade (so a stream class teacher only sees their stream); falls back to the
     * whole grade when the teacher owns the unstreamed grade directly. Returns an empty set when the
     * teacher has no class-teacher scope for the grade (callers have already passed ownsClass).
     */
    private java.util.Set<Long> resolveOwnedLearnerIds(User teacher, Long gradeId) {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        Staff staff = resolveCurrentStaff(teacher);
        if (staff == null) {
            return ids;
        }
        Grade grade = gradeRepository.findById(gradeId).orElse(null);
        if (grade == null) {
            return ids;
        }

        // Streams of this grade the teacher is class teacher of.
        List<GradeStream> ownedStreams = gradeStreamRepository.findAllByClassTeacherId(staff.getId()).stream()
                .filter(s -> s.getGrade() != null && gradeId.equals(s.getGrade().getId()))
                .collect(java.util.stream.Collectors.toList());

        if (!ownedStreams.isEmpty()) {
            for (GradeStream stream : ownedStreams) {
                for (Student s : loadLearners(grade, stream)) {
                    ids.add(s.getId());
                }
            }
            return ids;
        }

        // Otherwise the teacher owns the whole grade (unstreamed) — all its non-deleted learners.
        for (Student s : loadLearners(grade, null)) {
            ids.add(s.getId());
        }
        return ids;
    }

    @Override
    @Transactional
    public CustomResponse<String> markClassAttendance(
            com.EduePoa.EP.academics.dto.request.AttendanceRequestDTO request) {
        User teacher = resolveCurrentUser();
        if (teacher == null) {
            return error(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        if (request == null || request.getGradeId() == null) {
            return error(HttpStatus.BAD_REQUEST, "gradeId is required");
        }
        // Teacher must be the class teacher of this grade.
        CustomResponse<String> ownership = ownsClass(teacher, request.getGradeId(), null);
        if (ownership != null) return ownership;

        // Scope to the teacher's actual class: a stream class teacher may only mark learners in
        // their own stream, not every learner in the grade. Reject any student outside that scope
        // (defence beyond the grade-membership check the underlying service already performs).
        java.util.Set<Long> allowedStudentIds = resolveOwnedLearnerIds(teacher, request.getGradeId());
        if (request.getStudents() != null) {
            boolean anyForeign = request.getStudents().stream()
                    .anyMatch(s -> s.getStudentId() == null || !allowedStudentIds.contains(s.getStudentId()));
            if (anyForeign) {
                return error(HttpStatus.FORBIDDEN,
                        "One or more learners are not in your class and cannot be marked");
            }
        }

        String message = attendanceService.markAttendance(request);
        CustomResponse<String> r = new CustomResponse<>();
        r.setStatusCode(HttpStatus.OK.value());
        r.setMessage(message);
        r.setEntity(message);
        return r;
    }

    @Override
    public CustomResponse<java.util.List<com.EduePoa.EP.academics.dto.response.AttendanceSummaryDto>>
            getClassAttendanceSummary(Long gradeId, Long streamId, Long termId, Integer year,
                                      java.time.LocalDate from, java.time.LocalDate to) {
        User teacher = resolveCurrentUser();
        if (teacher == null) {
            return error(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        // When a specific stream is requested, the teacher must own that stream; otherwise ownership
        // of the grade (any owned stream of it, or the whole unstreamed grade) is required.
        CustomResponse<java.util.List<com.EduePoa.EP.academics.dto.response.AttendanceSummaryDto>> ownership =
                ownsClass(teacher, gradeId, streamId);
        if (ownership != null) return ownership;

        // Resolve the date window: a term (its start/end dates for the given year) takes precedence
        // over explicit from/to. When neither is supplied the underlying service defaults to
        // start-of-month .. today.
        java.time.LocalDate rangeFrom = from;
        java.time.LocalDate rangeTo = to;
        if (termId != null) {
            Optional<Term> termOpt = termResolver.resolveById(termId);
            if (termOpt.isEmpty()) {
                return error(HttpStatus.BAD_REQUEST, "Invalid termId (use 1, 2 or 3)");
            }
            Term term = termOpt.get();
            rangeFrom = term.getStartDate();
            rangeTo = term.getEndDate();
            // The hardcoded Term windows are for a reference year; align them to the requested year.
            if (year != null) {
                rangeFrom = rangeFrom.withYear(year);
                rangeTo = rangeTo.withYear(year);
            }
        }

        var all = attendanceService.getSummary(gradeId, rangeFrom, rangeTo);

        // Scope to the teacher's own class. If a specific stream is requested, restrict to just that
        // stream's learners; otherwise include all the teacher's owned learners in the grade.
        java.util.Set<Long> allowedStudentIds;
        if (streamId != null) {
            GradeStream stream = gradeStreamRepository.findById(streamId).orElse(null);
            Grade grade = gradeRepository.findById(gradeId).orElse(null);
            allowedStudentIds = new java.util.HashSet<>();
            if (stream != null && grade != null) {
                for (Student s : loadLearners(grade, stream)) {
                    allowedStudentIds.add(s.getId());
                }
            }
        } else {
            allowedStudentIds = resolveOwnedLearnerIds(teacher, gradeId);
        }

        java.util.List<com.EduePoa.EP.academics.dto.response.AttendanceSummaryDto> scoped =
                (all == null) ? java.util.List.of()
                        : all.stream()
                            .filter(s -> allowedStudentIds.contains(s.getStudentId()))
                            .collect(java.util.stream.Collectors.toList());

        CustomResponse<java.util.List<com.EduePoa.EP.academics.dto.response.AttendanceSummaryDto>> r =
                new CustomResponse<>();
        r.setStatusCode(HttpStatus.OK.value());
        r.setMessage(scoped.isEmpty() ? "No attendance records for this period" : "Attendance summary retrieved");
        r.setEntity(scoped);
        return r;
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /**
     * Returns null when the teacher owns the assignment; otherwise a typed 403 response. Ownership is
     * the active subject assignment for the authenticated teacher (stream-agnostic or matching stream).
     */
    private <T> CustomResponse<T> ownsAssignment(User teacher, TeacherSubjectAssignment assignment) {
        if (assignment.getTeacher() != null && assignment.getTeacher().getId() != null
                && assignment.getTeacher().getId().equals(teacher.getId())
                && (assignment.getActive() == null || assignment.getActive())) {
            return null;
        }
        // Fall back to the repository authorization check (covers equivalent assignments).
        Long gradeId = assignment.getGrade() == null ? null : assignment.getGrade().getId();
        Long subjectId = assignment.getAcademicSubject() == null ? null : assignment.getAcademicSubject().getId();
        Year year = assignment.getYear() == null ? Year.now() : assignment.getYear();
        if (gradeId != null && subjectId != null
                && assignmentRepository.isTeacherAuthorized(teacher.getId(), gradeId, subjectId, year)) {
            return null;
        }
        return error(HttpStatus.FORBIDDEN, "You do not own this assignment");
    }

    /**
     * Returns null when the teacher is the class teacher of the grade/stream; otherwise a typed 403.
     * A stream match or a whole-grade match both authorize.
     */
    private <T> CustomResponse<T> ownsClass(User teacher, Long gradeId, Long streamId) {
        if (gradeId == null) {
            return error(HttpStatus.BAD_REQUEST, "gradeId is required");
        }
        Staff staff = resolveCurrentStaff(teacher);
        if (staff == null) {
            return error(HttpStatus.FORBIDDEN, "No staff record linked to your account");
        }
        // Stream-level ownership.
        if (streamId != null) {
            boolean ownsStream = gradeStreamRepository.findAllByClassTeacherId(staff.getId()).stream()
                    .anyMatch(s -> s.getId().equals(streamId));
            if (ownsStream) return null;
        }
        // Whole-grade ownership (grade has no stream, or teacher owns the grade directly).
        boolean ownsGrade = gradeRepository.findAllByClassTeacherId(staff.getId()).stream()
                .anyMatch(g -> g.getId().equals(gradeId));
        if (ownsGrade) return null;

        // If no streamId was supplied, also allow when the teacher owns any stream of this grade.
        if (streamId == null) {
            boolean ownsAnyStreamOfGrade = gradeStreamRepository.findAllByClassTeacherId(staff.getId()).stream()
                    .anyMatch(s -> s.getGrade() != null && gradeId.equals(s.getGrade().getId()));
            if (ownsAnyStreamOfGrade) return null;
        }
        return error(HttpStatus.FORBIDDEN, "You are not the class teacher for this class");
    }

    /** Load non-deleted learners for a stream (if present) or whole grade. */
    private List<Student> loadLearners(Grade grade, GradeStream stream) {
        if (grade == null) return Collections.emptyList();
        List<Student> raw = stream != null
                ? studentRepository.findByGradeStreamId(stream.getId())
                : studentRepository.findByGradeId(grade.getId());
        List<Student> active = new ArrayList<>();
        for (Student s : raw) {
            if (!Boolean.TRUE.equals(s.getIsDeleted())) {
                active.add(s);
            }
        }
        return active;
    }

    /**
     * Resolve the effective term for a request against an assignment. If the assignment is
     * term-scoped, the request term must match it. If term-agnostic, the request term is used.
     */
    private Term resolveTermForAssignment(TeacherSubjectAssignment assignment, Long termId) {
        if (termId == null) {
            return assignment.getTerm(); // may be null for term-agnostic assignments -> invalid
        }
        Optional<Term> requested = termResolver.resolveById(termId);
        if (requested.isEmpty()) return null;
        if (assignment.getTerm() != null && assignment.getTerm() != requested.get()) {
            return null; // mismatch with the assignment's bound term
        }
        return requested.get();
    }

    private String buildStudentName(Student s) {
        StringBuilder sb = new StringBuilder();
        if (s.getFirstName() != null) sb.append(s.getFirstName().trim());
        if (s.getMiddleName() != null && !s.getMiddleName().isBlank()) sb.append(' ').append(s.getMiddleName().trim());
        if (s.getLastName() != null && !s.getLastName().isBlank()) sb.append(' ').append(s.getLastName().trim());
        return sb.toString().trim();
    }
}
