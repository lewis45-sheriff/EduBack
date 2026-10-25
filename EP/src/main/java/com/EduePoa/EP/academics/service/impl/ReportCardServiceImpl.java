package com.EduePoa.EP.academics.service.impl;

import com.EduePoa.EP.Authentication.Enum.AttendanceStatus;
import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.assessment.entity.CompetencyEvidence;
import com.EduePoa.EP.academics.assessment.entity.PerformanceLevel;
import com.EduePoa.EP.academics.assessment.repository.CompetencyEvidenceRepository;
import com.EduePoa.EP.academics.assessment.service.RankingService;
import com.EduePoa.EP.academics.common.TermResolver;
import com.EduePoa.EP.Finance.Finance;
import com.EduePoa.EP.Finance.FinanceRepository;
import com.EduePoa.EP.Scoring.StudentsScore;
import com.EduePoa.EP.Scoring.StudentsScoreRepository;
import com.EduePoa.EP.academics.dto.response.ReportCardDto;
import com.EduePoa.EP.academics.dto.response.ReportCardListItemDto;
import com.EduePoa.EP.academics.curriculum.entity.CbcValue;
import com.EduePoa.EP.academics.curriculum.repository.CbcValueRepository;
import com.EduePoa.EP.academics.entity.Attendance;
import com.EduePoa.EP.academics.entity.CbcGradeResult;
import com.EduePoa.EP.academics.repository.AttendanceRepository;
import com.EduePoa.EP.academics.repository.CbcGradeResultRepository;
import com.EduePoa.EP.academics.service.ReportCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Assembles a {@link ReportCardDto} from the CBC results, competency evidence and attendance.
 * The report is curriculum-stage aware: sections are populated only when data exists, and ranking
 * appears only when a school ranking policy is enabled.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportCardServiceImpl implements ReportCardService {

    private final StudentRepository studentRepository;
    private final CbcGradeResultRepository cbcGradeResultRepository;
    private final CompetencyEvidenceRepository competencyEvidenceRepository;
    private final AttendanceRepository attendanceRepository;
    private final RankingService rankingService;
    private final TermResolver termResolver;
    private final StudentsScoreRepository studentsScoreRepository;
    private final FinanceRepository financeRepository;
    private final CbcValueRepository cbcValueRepository;

    @Override
    public CustomResponse<ReportCardDto> getReportCard(Long studentId, Long termId, int year) {
        CustomResponse<ReportCardDto> response = new CustomResponse<>();
        try {
            Optional<Student> studentOpt = studentRepository.findById(studentId);
            Optional<Term> termOpt = termResolver.resolveById(termId);
            if (studentOpt.isEmpty() || termOpt.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Student or Term not found");
                return response;
            }
            Student student = studentOpt.get();
            Term term = termOpt.get();
            Year acaYear = Year.of(year);

            ReportCardDto dto = new ReportCardDto();
            dto.setStudentId(student.getId());
            dto.setStudentName(student.getFirstName() + " " + student.getLastName());
            dto.setAdmissionNumber(student.getAdmissionNumber());
            dto.setGradeName(student.getGradeName());
            dto.setStreamName(student.getStreamName());
            dto.setAcademicYear(year);
            dto.setTermName(term.name());

            populateLearningAreas(dto, student, term, acaYear);
            populateExamColumns(dto, student, term, acaYear);
            populateIndividualAssessments(dto, student, term, acaYear);
            populateCompetencies(dto, student, term, acaYear);
            populateValueCatalogue(dto);
            populateAttendance(dto, student, term);
            populateRanking(dto, student, term, acaYear);
            populateFees(dto, student, term, acaYear);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Report card assembled");
            response.setEntity(dto);
        } catch (Exception e) {
            log.error("Error assembling report card for student {}: {}", studentId, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error assembling report card: " + e.getMessage());
        }
        return response;
    }

    private void populateLearningAreas(ReportCardDto dto, Student student, Term term, Year acaYear) {
        List<CbcGradeResult> results = cbcGradeResultRepository
                .findByStudentAndTermAndYear(student, term, acaYear);
        for (CbcGradeResult r : results) {
            ReportCardDto.LearningAreaResult la = new ReportCardDto.LearningAreaResult();
            if (r.getAcademicSubject() != null) {
                la.setLearningAreaId(r.getAcademicSubject().getId());
                la.setLearningAreaName(r.getAcademicSubject().getSubjectName());
            }
            la.setNormalizedScore(r.getNormalizedScore());
            la.setPerformancePoints(r.getPerformancePoints());
            la.setTeacherComment(r.getTeacherComment());
            PerformanceLevel pl = r.getPerformanceLevel();
            if (pl != null) {
                la.setPerformanceLevelCode(pl.getCode());
                la.setPerformanceLevelLabel(pl.getLabel());
                if (r.getAssessmentFrameworkId() != null && pl.getAssessmentFramework() != null) {
                    dto.setAssessmentFrameworkCode(pl.getAssessmentFramework().getCode());
                }
            } else {
                la.setPerformanceLevelLabel(r.getCbcLabel());
            }
            dto.getLearningAreas().add(la);

            // The class-teacher remark is stored per learning-area row but represents a single
            // per-learner remark for the term (fanned out across rows when recorded). Surface the
            // first non-blank one as the report's overall teacher comment for the JSON consumers.
            if (dto.getTeacherOverallComment() == null
                    && r.getTeacherComment() != null && !r.getTeacherComment().isBlank()) {
                dto.setTeacherOverallComment(r.getTeacherComment());
            }
        }
    }

    /**
     * Fills the per-subject Opener / Midterm / Endterm raw marks and their average onto the
     * learning-area rows, and computes the exam-column totals (the "TOTAL" row of a report form).
     *
     * Raw marks come from {@link StudentsScore} rows for the term/year, bucketed by exam-type name
     * (matched case-insensitively on "opener"/"mid"/"end"). A missing exam leaves that column null
     * so the report can show a blank rather than a fabricated 0.
     */
    private void populateExamColumns(ReportCardDto dto, Student student, Term term, Year acaYear) {
        List<StudentsScore> scores = studentsScoreRepository
                .findByStudentAndTermAndYear(student, term, acaYear);
        if (scores.isEmpty()) {
            return;
        }

        // subjectId -> the learning-area row already built in populateLearningAreas.
        Map<Long, ReportCardDto.LearningAreaResult> bySubjectId = new java.util.HashMap<>();
        for (ReportCardDto.LearningAreaResult la : dto.getLearningAreas()) {
            if (la.getLearningAreaId() != null) {
                bySubjectId.put(la.getLearningAreaId(), la);
            }
        }

        for (StudentsScore s : scores) {
            if (s.getAcademicSubject() == null || s.getExamType() == null) {
                continue;
            }
            Long subjectId = s.getAcademicSubject().getId();
            ReportCardDto.LearningAreaResult la = bySubjectId.get(subjectId);
            if (la == null) {
                // A subject with raw marks but no CBC aggregate row — surface it anyway.
                la = new ReportCardDto.LearningAreaResult();
                la.setLearningAreaId(subjectId);
                la.setLearningAreaName(s.getAcademicSubject().getSubjectName());
                dto.getLearningAreas().add(la);
                bySubjectId.put(subjectId, la);
            }

            BigDecimal mark = s.effectiveRawScore();
            if (mark == null) {
                continue;
            }
            String examName = s.getExamType().getName() == null
                    ? "" : s.getExamType().getName().toLowerCase();
            if (examName.contains("opener")) {
                la.setOpenerScore(mark);
            } else if (examName.contains("mid")) {
                la.setMidtermScore(mark);
            } else if (examName.contains("end")) {
                la.setEndtermScore(mark);
            }
            // Exam types that don't match any bucket are ignored for the three fixed columns.
        }

        // Per-subject average over the available exam marks, and the column totals.
        BigDecimal openerTotal = null;
        BigDecimal midtermTotal = null;
        BigDecimal endtermTotal = null;
        BigDecimal averageTotal = null;

        for (ReportCardDto.LearningAreaResult la : dto.getLearningAreas()) {
            List<BigDecimal> present = new ArrayList<>();
            if (la.getOpenerScore() != null) present.add(la.getOpenerScore());
            if (la.getMidtermScore() != null) present.add(la.getMidtermScore());
            if (la.getEndtermScore() != null) present.add(la.getEndtermScore());
            if (!present.isEmpty()) {
                BigDecimal sum = present.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal avg = sum.divide(BigDecimal.valueOf(present.size()), 2, RoundingMode.HALF_UP);
                la.setAverageScore(avg);
            }

            openerTotal = addNullable(openerTotal, la.getOpenerScore());
            midtermTotal = addNullable(midtermTotal, la.getMidtermScore());
            endtermTotal = addNullable(endtermTotal, la.getEndtermScore());
            averageTotal = addNullable(averageTotal, la.getAverageScore());
        }

        dto.setOpenerTotal(openerTotal);
        dto.setMidtermTotal(midtermTotal);
        dto.setEndtermTotal(endtermTotal);
        dto.setAverageTotal(averageTotal);
    }

    /** Adds a possibly-null addend to a possibly-null running total; null stays null until a value appears. */
    private static BigDecimal addNullable(BigDecimal total, BigDecimal addend) {
        if (addend == null) {
            return total;
        }
        return total == null ? addend : total.add(addend);
    }

    /**
     * Attaches the student's fee snapshot for the term/year from the Finance module. Leaves
     * {@code dto.fees} null when there is no finance record (so the report shows nothing rather
     * than a misleading zero balance).
     */
    private void populateFees(ReportCardDto dto, Student student, Term term, Year acaYear) {
        Optional<Finance> financeOpt =
                financeRepository.findByStudentIdAndTermAndYear(student.getId(), term, acaYear);
        if (financeOpt.isEmpty()) {
            // Fall back to the student's latest finance record if the exact term/year is absent.
            financeOpt = financeRepository.findByStudentId(student.getId());
        }
        if (financeOpt.isEmpty()) {
            return;
        }
        Finance f = financeOpt.get();
        BigDecimal balance = f.getBalance() != null ? f.getBalance() : BigDecimal.ZERO;

        ReportCardDto.FeesSummary fees = new ReportCardDto.FeesSummary();
        fees.setTotalFeeAmount(f.getTotalFeeAmount());
        fees.setPaidAmount(f.getPaidAmount());
        fees.setBalance(balance);
        boolean cleared = balance.compareTo(BigDecimal.ZERO) <= 0;
        fees.setCleared(cleared);
        if (balance.compareTo(BigDecimal.ZERO) > 0) {
            fees.setStatus("OUTSTANDING");
        } else if (balance.compareTo(BigDecimal.ZERO) < 0) {
            fees.setStatus("OVERPAID");
        } else {
            fees.setStatus("CLEARED");
        }
        dto.setFees(fees);
    }

    @Override
    public CustomResponse<List<ReportCardListItemDto>> listReportCards(
            Long gradeId, Long streamId, Long termId, int year) {
        CustomResponse<List<ReportCardListItemDto>> response = new CustomResponse<>();
        try {
            Optional<Term> termOpt = termResolver.resolveById(termId);
            if (termOpt.isEmpty()) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Invalid term");
                return response;
            }
            Term term = termOpt.get();
            Year acaYear = Year.of(year);

            // Students for the grade (optionally narrowed to a stream). Both finders are tenant-aware.
            List<Student> students = (streamId != null)
                    ? studentRepository.findByGradeStreamId(streamId)
                    : studentRepository.findByGradeId(gradeId);

            List<ReportCardListItemDto> items = new ArrayList<>();
            for (Student s : students) {
                if (Boolean.TRUE.equals(s.getIsDeleted())) {
                    continue;
                }
                boolean ready = !cbcGradeResultRepository
                        .findByStudentAndTermAndYear(s, term, acaYear).isEmpty();
                items.add(ReportCardListItemDto.builder()
                        .studentId(s.getId())
                        .studentName((s.getFirstName() == null ? "" : s.getFirstName()) + " "
                                + (s.getLastName() == null ? "" : s.getLastName()).trim())
                        .admissionNumber(s.getAdmissionNumber())
                        .gradeName(s.getGradeName())
                        .streamName(s.getStreamName())
                        .ready(ready)
                        .status(ready ? "Ready" : "Pending")
                        .build());
            }

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Report card list assembled (" + items.size() + " learner(s))");
            response.setEntity(items);
        } catch (Exception e) {
            log.error("Error listing report cards for grade {} term {} year {}: {}",
                    gradeId, termId, year, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error listing report cards: " + e.getMessage());
        }
        return response;
    }

    /**
     * Lists every individual assessment/exam recorded for the learner this term, one row per
     * StudentsScore (assessment name = exam type). This is the raw evidence behind the overall
     * aggregate in {@link #populateLearningAreas} — it is NOT summed into the overall, so there is
     * no double counting. Rows are ordered by learning area then assessment name for a stable table.
     */
    private void populateIndividualAssessments(ReportCardDto dto, Student student, Term term, Year acaYear) {
        List<StudentsScore> scores = studentsScoreRepository
                .findByStudentAndTermAndYear(student, term, acaYear);
        if (scores.isEmpty()) {
            return;
        }
        // De-duplicate to one row per (subject + exam type). Legacy data can contain more than one
        // score row for the same student/subject/exam-type/term/year; the latest row (highest id)
        // wins so the report never shows the same exam twice.
        java.util.Map<String, StudentsScore> latestByKey = new java.util.LinkedHashMap<>();
        for (StudentsScore s : scores) {
            if (s.getAcademicSubject() == null || s.getExamType() == null) {
                continue;
            }
            if (s.effectiveRawScore() == null) {
                // Missing/ungraded assessment — skip rather than record a fabricated 0.
                continue;
            }
            String key = s.getAcademicSubject().getId() + "|" + s.getExamType().getId();
            StudentsScore existing = latestByKey.get(key);
            if (existing == null || (s.getId() != null && existing.getId() != null
                    && s.getId() > existing.getId())) {
                latestByKey.put(key, s);
            }
        }

        List<ReportCardDto.IndividualAssessment> rows = new ArrayList<>();
        for (StudentsScore s : latestByKey.values()) {
            BigDecimal score = s.effectiveRawScore();
            ReportCardDto.IndividualAssessment ia = new ReportCardDto.IndividualAssessment();
            ia.setLearningAreaId(s.getAcademicSubject().getId());
            ia.setLearningAreaName(s.getAcademicSubject().getSubjectName());
            ia.setAssessmentName(s.getExamType().getName());
            ia.setScore(score);

            // Max score: prefer the per-row maximum, else the exam type's configured max.
            BigDecimal max = s.getMaximumScore();
            if (max == null && s.getExamType().getMaxScore() != null) {
                max = BigDecimal.valueOf(s.getExamType().getMaxScore());
            }
            ia.setMaxScore(max);
            if (max != null && max.compareTo(BigDecimal.ZERO) > 0) {
                ia.setPercentage(score.multiply(BigDecimal.valueOf(100))
                        .divide(max, 2, RoundingMode.HALF_UP));
            }
            rows.add(ia);
        }
        rows.sort((a, b) -> {
            int byArea = String.valueOf(a.getLearningAreaName())
                    .compareToIgnoreCase(String.valueOf(b.getLearningAreaName()));
            if (byArea != 0) return byArea;
            return String.valueOf(a.getAssessmentName())
                    .compareToIgnoreCase(String.valueOf(b.getAssessmentName()));
        });
        dto.setIndividualAssessments(rows);
    }

    /**
     * Fills the school's CBC core values catalogue (names only) for the current tenant. These are
     * the values the school promotes, shown on the report for reference — NOT per-learner
     * achievements. Per-learner value observations are not captured yet, so dto.values stays empty.
     */
    private void populateValueCatalogue(ReportCardDto dto) {
        try {
            List<String> names = cbcValueRepository.findAllByOrderBySequenceAsc().stream()
                    .map(CbcValue::getName)
                    .filter(n -> n != null && !n.isBlank())
                    .toList();
            dto.setValueCatalogue(names);
        } catch (Exception e) {
            // Values are reference-only; never fail the whole report if the catalogue read fails.
            log.warn("Could not load value catalogue for report card: {}", e.getMessage());
        }
    }

    private void populateCompetencies(ReportCardDto dto, Student student, Term term, Year acaYear) {
        List<CompetencyEvidence> evidences = competencyEvidenceRepository
                .findByStudentAndTermAndAcademicYear(student, term, acaYear);
        // Flag whether any competency was actually assessed, so the report can distinguish a
        // genuine "not yet assessed" state from fabricated results.
        dto.setCompetenciesAssessed(!evidences.isEmpty());
        for (CompetencyEvidence ev : evidences) {
            ReportCardDto.CompetencyResult cr = new ReportCardDto.CompetencyResult();
            if (ev.getCompetency() != null) {
                cr.setCompetencyId(ev.getCompetency().getId());
                cr.setCompetencyName(ev.getCompetency().getName());
            }
            if (ev.getPerformanceLevel() != null) {
                cr.setLevelLabel(ev.getPerformanceLevel().getLabel());
            }
            cr.setComment(ev.getComment() != null ? ev.getComment() : ev.getEvidenceDescription());
            dto.getCompetencies().add(cr);
        }
    }

    private void populateAttendance(ReportCardDto dto, Student student, Term term) {
        // Aggregate daily attendance across the term's date window (records are not duplicated here).
        List<Attendance> records = attendanceRepository
                .findByDateBetween(term.getStartDate(), term.getEndDate())
                .stream()
                .filter(a -> a.getStudent() != null && a.getStudent().getId().equals(student.getId()))
                .toList();
        ReportCardDto.AttendanceSummary summary = new ReportCardDto.AttendanceSummary();
        summary.setDaysPresent((int) records.stream().filter(a -> a.getStatus() == AttendanceStatus.PRESENT).count());
        summary.setDaysAbsent((int) records.stream().filter(a -> a.getStatus() == AttendanceStatus.ABSENT).count());
        summary.setDaysLate((int) records.stream().filter(a -> a.getStatus() == AttendanceStatus.LATE).count());
        summary.setDaysExcused((int) records.stream().filter(a -> a.getStatus() == AttendanceStatus.EXCUSED).count());
        dto.setAttendance(summary);
    }

    private void populateRanking(ReportCardDto dto, Student student, Term term, Year acaYear) {
        if (rankingService.isRankingEnabled(student, term, acaYear)) {
            rankingService.resolvePosition(student, term, acaYear).ifPresent(dto::setPosition);
            dto.setRankingScope(rankingService.resolveScopeLabel(student, term, acaYear));
        }
        // When disabled, position and rankingScope remain null and nothing is shown.
    }
}
