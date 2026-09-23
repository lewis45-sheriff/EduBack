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
import com.EduePoa.EP.academics.dto.response.ReportCardDto;
import com.EduePoa.EP.academics.entity.Attendance;
import com.EduePoa.EP.academics.entity.CbcGradeResult;
import com.EduePoa.EP.academics.repository.AttendanceRepository;
import com.EduePoa.EP.academics.repository.CbcGradeResultRepository;
import com.EduePoa.EP.academics.service.ReportCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.List;
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
            populateCompetencies(dto, student, term, acaYear);
            populateAttendance(dto, student, term);
            populateRanking(dto, student, term, acaYear);

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
        }
    }

    private void populateCompetencies(ReportCardDto dto, Student student, Term term, Year acaYear) {
        List<CompetencyEvidence> evidences = competencyEvidenceRepository
                .findByStudentAndTermAndAcademicYear(student, term, acaYear);
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
