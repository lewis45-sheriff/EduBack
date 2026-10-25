package com.EduePoa.EP.academics.service;

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
import com.EduePoa.EP.academics.assessment.repository.StudentTermSummaryRepository;
import com.EduePoa.EP.academics.assessment.service.RankingService;
import com.EduePoa.EP.academics.common.TermResolver;
import com.EduePoa.EP.academics.dto.request.SaveScoresRequest;
import com.EduePoa.EP.academics.dto.request.SubmitAssessmentRequest;
import com.EduePoa.EP.academics.dto.response.AssignmentRosterDto;
import com.EduePoa.EP.academics.dto.response.SaveScoresResultDto;
import com.EduePoa.EP.academics.dto.response.SubmitAssessmentResultDto;
import com.EduePoa.EP.academics.dto.response.TeacherAssignmentDto;
import com.EduePoa.EP.academics.entity.AcademicSubject;
import com.EduePoa.EP.academics.entity.TeacherSubjectAssignment;
import com.EduePoa.EP.academics.repository.TeacherSubjectAssignmentRepository;
import com.EduePoa.EP.academics.service.impl.TeacherAssessmentServiceImpl;
import com.EduePoa.EP.academics.service.CbcCalculatorService;
import com.EduePoa.EP.academics.service.ReportCardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the teacher-scoped assessment service. Focus: authenticated-teacher resolution,
 * ownership enforcement (403), idempotent marks upsert, null (blank) handling and submission state.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeacherAssessmentServiceImplTest {

    private TeacherSubjectAssignmentRepository assignmentRepository;
    private StudentsScoreRepository studentsScoreRepository;
    private StudentRepository studentRepository;
    private ExamTypeRepository examTypeRepository;
    private GradeRepository gradeRepository;
    private GradeStreamRepository gradeStreamRepository;
    private StaffRepository staffRepository;
    private UserRepository userRepository;
    private TermResolver termResolver;
    private CbcCalculatorService cbcCalculatorService;
    private StudentTermSummaryRepository studentTermSummaryRepository;
    private RankingService rankingService;
    private AttendanceService attendanceService;
    private ReportCardService reportCardService;
    private com.EduePoa.EP.academics.repository.CbcGradeResultRepository cbcGradeResultRepository;

    private TeacherAssessmentServiceImpl service;

    private static final String TEACHER_EMAIL = "teacher@school.test";
    private static final Long TEACHER_ID = 7L;
    private static final int YEAR = 2026;

    @BeforeEach
    void setUp() {
        assignmentRepository = mock(TeacherSubjectAssignmentRepository.class);
        studentsScoreRepository = mock(StudentsScoreRepository.class);
        studentRepository = mock(StudentRepository.class);
        examTypeRepository = mock(ExamTypeRepository.class);
        gradeRepository = mock(GradeRepository.class);
        gradeStreamRepository = mock(GradeStreamRepository.class);
        staffRepository = mock(StaffRepository.class);
        userRepository = mock(UserRepository.class);
        termResolver = new TermResolver();
        cbcCalculatorService = mock(CbcCalculatorService.class);
        studentTermSummaryRepository = mock(StudentTermSummaryRepository.class);
        rankingService = mock(RankingService.class);
        attendanceService = mock(AttendanceService.class);
        reportCardService = mock(ReportCardService.class);
        cbcGradeResultRepository = mock(com.EduePoa.EP.academics.repository.CbcGradeResultRepository.class);

        service = new TeacherAssessmentServiceImpl(
                assignmentRepository, studentsScoreRepository, studentRepository, examTypeRepository,
                gradeRepository, gradeStreamRepository, staffRepository, userRepository, termResolver,
                cbcCalculatorService, studentTermSummaryRepository, rankingService, attendanceService,
                reportCardService, cbcGradeResultRepository);

        // Authenticate as the teacher.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(TEACHER_EMAIL, "x"));
        when(userRepository.findByEmail(TEACHER_EMAIL)).thenReturn(Optional.of(teacher()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------------- fixtures ----------------

    private User teacher() {
        User u = new User();
        u.setId(TEACHER_ID);
        u.setEmail(TEACHER_EMAIL);
        u.setFirstName("Jane");
        u.setLastName("Doe");
        return u;
    }

    private Grade grade() {
        Grade g = new Grade();
        g.setId(10L);
        g.setName("Grade 6");
        return g;
    }

    private AcademicSubject subject() {
        AcademicSubject s = new AcademicSubject();
        s.setId(5L);
        s.setSubjectName("Mathematics");
        return s;
    }

    private ExamType examType() {
        ExamType e = new ExamType();
        e.setId(3L);
        e.setName("Opener");
        e.setMaxScore(100L);
        e.setMinScore(0L);
        return e;
    }

    private TeacherSubjectAssignment ownedAssignment() {
        TeacherSubjectAssignment a = new TeacherSubjectAssignment();
        a.setId(99L);
        a.setTeacher(teacher());
        a.setGrade(grade());
        a.setAcademicSubject(subject());
        a.setYear(Year.of(YEAR));
        a.setTerm(Term.TERM_1);
        a.setActive(true);
        return a;
    }

    private Student student(Long id, String adm, boolean deleted) {
        Student s = new Student();
        s.setId(id);
        s.setAdmissionNumber(adm);
        s.setFirstName("Learner" + id);
        s.setLastName("Test");
        s.setIsDeleted(deleted);
        s.setGrade(grade());
        return s;
    }

    // ---------------- tests ----------------

    @Test
    @DisplayName("1 & scope: getMyAssignments returns only the authenticated teacher's assignments")
    void myAssignments_onlyOwn() {
        when(assignmentRepository.findByTeacherIdAndYear(TEACHER_ID, Year.of(YEAR)))
                .thenReturn(List.of(ownedAssignment()));

        CustomResponse<List<TeacherAssignmentDto>> res = service.getMyAssignments(YEAR);

        assertThat(res.getStatusCode()).isEqualTo(200);
        assertThat(res.getEntity()).hasSize(1);
        assertThat(res.getEntity().get(0).getLearningAreaName()).isEqualTo("Mathematics");
        verify(assignmentRepository).findByTeacherIdAndYear(TEACHER_ID, Year.of(YEAR));
    }

    @Test
    @DisplayName("2: a teacher cannot retrieve another teacher's assignment roster (403)")
    void roster_notOwner_forbidden() {
        TeacherSubjectAssignment foreign = ownedAssignment();
        User other = new User();
        other.setId(999L);
        foreign.setTeacher(other);
        when(assignmentRepository.findById(99L)).thenReturn(Optional.of(foreign));
        when(assignmentRepository.isTeacherAuthorized(eq(TEACHER_ID), any(), any(), any()))
                .thenReturn(false);

        CustomResponse<AssignmentRosterDto> res = service.getRoster(99L, 3L, 1L, YEAR);

        assertThat(res.getStatusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("3,4,5,6: roster excludes soft-deleted learners, returns existing scores, null stays blank")
    void roster_ownedAssignment() {
        when(assignmentRepository.findById(99L)).thenReturn(Optional.of(ownedAssignment()));
        when(examTypeRepository.findById(3L)).thenReturn(Optional.of(examType()));

        Student s1 = student(101L, "ADM101", false);
        Student s2 = student(102L, "ADM102", false);
        Student deleted = student(103L, "ADM103", true);
        when(studentRepository.findByGradeId(10L)).thenReturn(new ArrayList<>(List.of(s1, s2, deleted)));

        // s1 has an existing score; s2 has none.
        StudentsScore sc1 = new StudentsScore();
        sc1.setStudent(s1);
        sc1.setRawScore(new BigDecimal("78"));
        sc1.setResultStatus(ResultStatus.DRAFT);
        when(studentsScoreRepository.findByGradeAndAcademicSubjectAndExamTypeAndTermAndYear(
                any(), any(), any(), eq(Term.TERM_1), eq(Year.of(YEAR))))
                .thenReturn(List.of(sc1));

        CustomResponse<AssignmentRosterDto> res = service.getRoster(99L, 3L, 1L, YEAR);

        assertThat(res.getStatusCode()).isEqualTo(200);
        AssignmentRosterDto dto = res.getEntity();
        assertThat(dto.getLearners()).hasSize(2); // deleted excluded
        AssignmentRosterDto.RosterLearner r1 = dto.getLearners().stream()
                .filter(l -> l.getStudentId().equals(101L)).findFirst().orElseThrow();
        AssignmentRosterDto.RosterLearner r2 = dto.getLearners().stream()
                .filter(l -> l.getStudentId().equals(102L)).findFirst().orElseThrow();
        assertThat(r1.getRawScore()).isEqualByComparingTo("78");
        assertThat(r2.getRawScore()).isNull(); // blank, never 0
        assertThat(dto.getMaxScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("7: saving a new mark creates a score with the authenticated teacher as subject teacher")
    void saveScores_createsNew() {
        when(assignmentRepository.findById(99L)).thenReturn(Optional.of(ownedAssignment()));
        when(examTypeRepository.findById(3L)).thenReturn(Optional.of(examType()));
        Student s1 = student(101L, "ADM101", false);
        when(studentRepository.findByGradeId(10L)).thenReturn(List.of(s1));
        when(studentsScoreRepository.findByStudentAndGradeAndTermAndAcademicSubjectAndExamTypeAndYear(
                any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(studentsScoreRepository.save(any(StudentsScore.class))).thenAnswer(inv -> inv.getArgument(0));

        SaveScoresRequest req = new SaveScoresRequest();
        req.setTermId(1L);
        req.setYear(YEAR);
        req.setExamTypeId(3L);
        SaveScoresRequest.ScoreEntry e = new SaveScoresRequest.ScoreEntry();
        e.setStudentId(101L);
        e.setRawScore(new BigDecimal("65"));
        req.setScores(List.of(e));

        CustomResponse<SaveScoresResultDto> res = service.saveScores(99L, req);

        assertThat(res.getStatusCode()).isEqualTo(200);
        assertThat(res.getEntity().getCreated()).isEqualTo(1);
        assertThat(res.getEntity().getUpdated()).isZero();

        org.mockito.ArgumentCaptor<StudentsScore> captor =
                org.mockito.ArgumentCaptor.forClass(StudentsScore.class);
        verify(studentsScoreRepository).save(captor.capture());
        StudentsScore saved = captor.getValue();
        assertThat(saved.getRawScore()).isEqualByComparingTo("65");
        assertThat(saved.getMaximumScore()).isEqualByComparingTo("100");
        assertThat(saved.getSubjectTeacher().getId()).isEqualTo(TEACHER_ID);
        assertThat(saved.getResultStatus()).isEqualTo(ResultStatus.DRAFT);
    }

    @Test
    @DisplayName("8 & 9: saving an existing mark updates the same record (no duplicate)")
    void saveScores_updatesExisting_noDuplicate() {
        when(assignmentRepository.findById(99L)).thenReturn(Optional.of(ownedAssignment()));
        when(examTypeRepository.findById(3L)).thenReturn(Optional.of(examType()));
        Student s1 = student(101L, "ADM101", false);
        when(studentRepository.findByGradeId(10L)).thenReturn(List.of(s1));

        StudentsScore existing = new StudentsScore();
        existing.setStudent(s1);
        existing.setRawScore(new BigDecimal("40"));
        existing.setResultStatus(ResultStatus.DRAFT);
        when(studentsScoreRepository.findByStudentAndGradeAndTermAndAcademicSubjectAndExamTypeAndYear(
                any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(existing));
        when(studentsScoreRepository.save(any(StudentsScore.class))).thenAnswer(inv -> inv.getArgument(0));

        SaveScoresRequest req = new SaveScoresRequest();
        req.setTermId(1L);
        req.setYear(YEAR);
        req.setExamTypeId(3L);
        SaveScoresRequest.ScoreEntry e = new SaveScoresRequest.ScoreEntry();
        e.setStudentId(101L);
        e.setRawScore(new BigDecimal("88"));
        req.setScores(List.of(e));

        CustomResponse<SaveScoresResultDto> res = service.saveScores(99L, req);

        assertThat(res.getEntity().getCreated()).isZero();
        assertThat(res.getEntity().getUpdated()).isEqualTo(1);
        assertThat(existing.getRawScore()).isEqualByComparingTo("88"); // same record mutated
        verify(studentsScoreRepository, times(1)).save(any(StudentsScore.class));
    }

    @Test
    @DisplayName("null rawScore is skipped as blank, never written")
    void saveScores_nullIsBlank() {
        when(assignmentRepository.findById(99L)).thenReturn(Optional.of(ownedAssignment()));
        when(examTypeRepository.findById(3L)).thenReturn(Optional.of(examType()));
        Student s1 = student(101L, "ADM101", false);
        when(studentRepository.findByGradeId(10L)).thenReturn(List.of(s1));

        SaveScoresRequest req = new SaveScoresRequest();
        req.setTermId(1L);
        req.setYear(YEAR);
        req.setExamTypeId(3L);
        SaveScoresRequest.ScoreEntry e = new SaveScoresRequest.ScoreEntry();
        e.setStudentId(101L);
        e.setRawScore(null);
        req.setScores(List.of(e));

        CustomResponse<SaveScoresResultDto> res = service.saveScores(99L, req);

        assertThat(res.getEntity().getSkippedBlank()).isEqualTo(1);
        verify(studentsScoreRepository, never()).save(any(StudentsScore.class));
    }

    @Test
    @DisplayName("10: an out-of-range score is rejected")
    void saveScores_outOfRangeRejected() {
        when(assignmentRepository.findById(99L)).thenReturn(Optional.of(ownedAssignment()));
        when(examTypeRepository.findById(3L)).thenReturn(Optional.of(examType()));
        Student s1 = student(101L, "ADM101", false);
        when(studentRepository.findByGradeId(10L)).thenReturn(List.of(s1));

        SaveScoresRequest req = new SaveScoresRequest();
        req.setTermId(1L);
        req.setYear(YEAR);
        req.setExamTypeId(3L);
        SaveScoresRequest.ScoreEntry e = new SaveScoresRequest.ScoreEntry();
        e.setStudentId(101L);
        e.setRawScore(new BigDecimal("150")); // > max 100
        req.setScores(List.of(e));

        CustomResponse<SaveScoresResultDto> res = service.saveScores(99L, req);

        assertThat(res.getStatusCode()).isEqualTo(400);
        assertThat(res.getEntity().getErrors()).isNotEmpty();
        verify(studentsScoreRepository, never()).save(any(StudentsScore.class));
    }

    @Test
    @DisplayName("a learner outside the authorized class is rejected")
    void saveScores_foreignStudentRejected() {
        when(assignmentRepository.findById(99L)).thenReturn(Optional.of(ownedAssignment()));
        when(examTypeRepository.findById(3L)).thenReturn(Optional.of(examType()));
        when(studentRepository.findByGradeId(10L)).thenReturn(List.of(student(101L, "ADM101", false)));

        SaveScoresRequest req = new SaveScoresRequest();
        req.setTermId(1L);
        req.setYear(YEAR);
        req.setExamTypeId(3L);
        SaveScoresRequest.ScoreEntry e = new SaveScoresRequest.ScoreEntry();
        e.setStudentId(500L); // not in roster
        e.setRawScore(new BigDecimal("50"));
        req.setScores(List.of(e));

        CustomResponse<SaveScoresResultDto> res = service.saveScores(99L, req);

        assertThat(res.getStatusCode()).isEqualTo(400);
        assertThat(res.getEntity().getErrors()).isNotEmpty();
        verify(studentsScoreRepository, never()).save(any(StudentsScore.class));
    }

    @Test
    @DisplayName("11: a teacher cannot submit an assignment they do not own (403)")
    void submit_notOwner_forbidden() {
        TeacherSubjectAssignment foreign = ownedAssignment();
        User other = new User();
        other.setId(999L);
        foreign.setTeacher(other);
        when(assignmentRepository.findById(99L)).thenReturn(Optional.of(foreign));
        when(assignmentRepository.isTeacherAuthorized(eq(TEACHER_ID), any(), any(), any()))
                .thenReturn(false);

        SubmitAssessmentRequest req = new SubmitAssessmentRequest();
        req.setTermId(1L);
        req.setYear(YEAR);
        req.setExamTypeId(3L);

        CustomResponse<SubmitAssessmentResultDto> res = service.submit(99L, req);

        assertThat(res.getStatusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("submit transitions DRAFT scores to SUBMITTED")
    void submit_transitionsToSubmitted() {
        when(assignmentRepository.findById(99L)).thenReturn(Optional.of(ownedAssignment()));
        when(examTypeRepository.findById(3L)).thenReturn(Optional.of(examType()));
        StudentsScore sc = new StudentsScore();
        sc.setResultStatus(ResultStatus.DRAFT);
        when(studentsScoreRepository.findByGradeAndAcademicSubjectAndExamTypeAndTermAndYear(
                any(), any(), any(), eq(Term.TERM_1), eq(Year.of(YEAR)))).thenReturn(List.of(sc));
        when(studentsScoreRepository.save(any(StudentsScore.class))).thenAnswer(inv -> inv.getArgument(0));

        SubmitAssessmentRequest req = new SubmitAssessmentRequest();
        req.setTermId(1L);
        req.setYear(YEAR);
        req.setExamTypeId(3L);

        CustomResponse<SubmitAssessmentResultDto> res = service.submit(99L, req);

        assertThat(res.getStatusCode()).isEqualTo(200);
        assertThat(sc.getResultStatus()).isEqualTo(ResultStatus.SUBMITTED);
        assertThat(res.getEntity().getSubmittedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("14: a teacher cannot compute results for a class they do not teach (403)")
    void compute_notClassTeacher_forbidden() {
        Staff staff = new Staff();
        staff.setId(55L);
        when(staffRepository.findByUserId(TEACHER_ID)).thenReturn(Optional.of(staff));
        when(gradeStreamRepository.findAllByClassTeacherId(55L)).thenReturn(List.of());
        when(gradeRepository.findAllByClassTeacherId(55L)).thenReturn(List.of());

        CustomResponse<?> res = service.computeResults(10L, null, 1L, YEAR);

        assertThat(res.getStatusCode()).isEqualTo(403);
        verify(cbcCalculatorService, never()).computeForGrade(any(), any(), anyInt());
    }

    @Test
    @DisplayName("compute delegates to the existing engine when the teacher owns the grade")
    void compute_ownsGrade_delegates() {
        Staff staff = new Staff();
        staff.setId(55L);
        when(staffRepository.findByUserId(TEACHER_ID)).thenReturn(Optional.of(staff));
        Grade g = grade();
        when(gradeStreamRepository.findAllByClassTeacherId(55L)).thenReturn(List.of());
        when(gradeRepository.findAllByClassTeacherId(55L)).thenReturn(List.of(g));
        CustomResponse<java.util.List<com.EduePoa.EP.academics.dto.response.CbcResultDto>> engineResult =
                new CustomResponse<>();
        engineResult.setStatusCode(200);
        engineResult.setMessage("Computed");
        engineResult.setEntity(List.of());
        when(cbcCalculatorService.computeForGrade(10L, 1L, YEAR)).thenReturn(engineResult);

        CustomResponse<?> res = service.computeResults(10L, null, 1L, YEAR);

        assertThat(res.getStatusCode()).isEqualTo(200);
        verify(cbcCalculatorService).computeForGrade(10L, 1L, YEAR);
    }

    @Test
    @DisplayName("13: a teacher cannot mark attendance for a class they do not teach (403)")
    void markAttendance_notClassTeacher_forbidden() {
        Staff staff = new Staff();
        staff.setId(55L);
        when(staffRepository.findByUserId(TEACHER_ID)).thenReturn(Optional.of(staff));
        when(gradeStreamRepository.findAllByClassTeacherId(55L)).thenReturn(List.of());
        when(gradeRepository.findAllByClassTeacherId(55L)).thenReturn(List.of());

        com.EduePoa.EP.academics.dto.request.AttendanceRequestDTO req =
                new com.EduePoa.EP.academics.dto.request.AttendanceRequestDTO();
        req.setGradeId(10L);

        CustomResponse<String> res = service.markClassAttendance(req);

        assertThat(res.getStatusCode()).isEqualTo(403);
        verify(attendanceService, never()).markAttendance(any());
    }

    @Test
    @DisplayName("student results: forbidden when the teacher is not the learner's class teacher")
    void studentResults_notClassTeacher_forbidden() {
        Student s = student(101L, "ADM101", false);
        when(studentRepository.findById(101L)).thenReturn(Optional.of(s));
        Staff staff = new Staff();
        staff.setId(55L);
        when(staffRepository.findByUserId(TEACHER_ID)).thenReturn(Optional.of(staff));
        when(gradeStreamRepository.findAllByClassTeacherId(55L)).thenReturn(List.of());
        when(gradeRepository.findAllByClassTeacherId(55L)).thenReturn(List.of());

        CustomResponse<?> res = service.getStudentResults(101L, YEAR);

        assertThat(res.getStatusCode()).isEqualTo(403);
        verify(reportCardService, never()).getReportCard(any(), any(), anyInt());
    }

    @Test
    @DisplayName("student results: owned learner aggregates only terms that have recorded data")
    void studentResults_ownedLearner_aggregatesTermsWithData() {
        Student s = student(101L, "ADM101", false);
        when(studentRepository.findById(101L)).thenReturn(Optional.of(s));
        Staff staff = new Staff();
        staff.setId(55L);
        when(staffRepository.findByUserId(TEACHER_ID)).thenReturn(Optional.of(staff));
        when(gradeRepository.findAllByClassTeacherId(55L)).thenReturn(List.of(grade()));
        when(gradeStreamRepository.findAllByClassTeacherId(55L)).thenReturn(List.of());

        // Term 1 has an exam; terms 2 and 3 have no data.
        com.EduePoa.EP.academics.dto.response.ReportCardDto t1 =
                new com.EduePoa.EP.academics.dto.response.ReportCardDto();
        t1.setTermName("TERM_1");
        com.EduePoa.EP.academics.dto.response.ReportCardDto.IndividualAssessment ex =
                new com.EduePoa.EP.academics.dto.response.ReportCardDto.IndividualAssessment();
        ex.setLearningAreaName("Mathematics");
        ex.setAssessmentName("Opener");
        ex.setScore(new java.math.BigDecimal("78"));
        ex.setMaxScore(new java.math.BigDecimal("100"));
        t1.getIndividualAssessments().add(ex);

        com.EduePoa.EP.academics.dto.response.ReportCardDto empty =
                new com.EduePoa.EP.academics.dto.response.ReportCardDto();
        empty.setTermName("TERM_2");

        CustomResponse<com.EduePoa.EP.academics.dto.response.ReportCardDto> r1 = new CustomResponse<>();
        r1.setStatusCode(200);
        r1.setEntity(t1);
        CustomResponse<com.EduePoa.EP.academics.dto.response.ReportCardDto> rEmpty = new CustomResponse<>();
        rEmpty.setStatusCode(200);
        rEmpty.setEntity(empty);

        when(reportCardService.getReportCard(101L, 1L, YEAR)).thenReturn(r1);
        when(reportCardService.getReportCard(101L, 2L, YEAR)).thenReturn(rEmpty);
        when(reportCardService.getReportCard(101L, 3L, YEAR)).thenReturn(rEmpty);

        CustomResponse<com.EduePoa.EP.academics.dto.response.StudentResultsDto> res =
                service.getStudentResults(101L, YEAR);

        assertThat(res.getStatusCode()).isEqualTo(200);
        assertThat(res.getEntity().getTerms()).hasSize(1);
        assertThat(res.getEntity().getTerms().get(0).getExams()).hasSize(1);
        assertThat(res.getEntity().getTerms().get(0).getExams().get(0).getAssessmentName()).isEqualTo("Opener");
    }

    @Test
    @DisplayName("18: missing authentication yields 401")
    void noAuth_unauthorized() {
        SecurityContextHolder.clearContext();
        CustomResponse<List<TeacherAssignmentDto>> res = service.getMyAssignments(YEAR);
        assertThat(res.getStatusCode()).isEqualTo(401);
    }
}
