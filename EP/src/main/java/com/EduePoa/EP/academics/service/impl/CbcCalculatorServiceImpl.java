package com.EduePoa.EP.academics.service.impl;


import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.Scoring.StudentsScore;
import com.EduePoa.EP.Scoring.StudentsScoreRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.assessment.entity.AssessmentFramework;
import com.EduePoa.EP.academics.assessment.entity.PerformanceLevel;
import com.EduePoa.EP.academics.assessment.enums.BroadPerformanceLevel;
import com.EduePoa.EP.academics.assessment.service.AssessmentCalculationService;
import com.EduePoa.EP.academics.assessment.service.FrameworkResolutionService;
import com.EduePoa.EP.academics.assessment.service.PerformanceLevelService;
import com.EduePoa.EP.academics.common.TermResolver;
import com.EduePoa.EP.academics.dto.response.CbcResultDto;
import com.EduePoa.EP.academics.entity.AcademicSubject;
import com.EduePoa.EP.academics.entity.CbcGradeResult;
import com.EduePoa.EP.academics.repository.CbcGradeResultRepository;
import com.EduePoa.EP.academics.service.CbcCalculatorService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Framework-driven CBC results engine.
 * <p>
 * The old implementation hard-coded {@code if (average >= 80) ...}. This version:
 * <ol>
 *   <li>resolves the {@link AssessmentFramework} that applies to the grade (via
 *       {@link FrameworkResolutionService});</li>
 *   <li>normalizes raw scores to a percentage using {@link AssessmentCalculationService}
 *       (preferring StudentsScore.rawScore/maximumScore, falling back to the legacy examScore);</li>
 *   <li>resolves the {@link PerformanceLevel} from configurable band data via
 *       {@link PerformanceLevelService} — no thresholds in code;</li>
 *   <li>persists the framework/version ids used so the result is reproducible.</li>
 * </ol>
 * When no framework is configured for a tenant/grade, it falls back to the four broad CBC levels
 * using generic quartile-style bands, clearly marked as a fallback, so existing behaviour keeps
 * working until curriculum data is seeded.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CbcCalculatorServiceImpl implements CbcCalculatorService {

    private static final String CALC_VERSION = "cbc-engine-v2";

    private final CbcGradeResultRepository cbcResultRepository;
    private final StudentsScoreRepository studentsScoreRepository;
    private final GradeRepository gradeRepository;
    private final StudentRepository studentRepository;
    private final TermResolver termResolver;
    private final FrameworkResolutionService frameworkResolutionService;
    private final PerformanceLevelService performanceLevelService;
    private final AssessmentCalculationService calculationService;

    @Override
    @Transactional
    public CustomResponse<List<CbcResultDto>> computeForGrade(Long gradeId, Long termId, int year) {
        CustomResponse<List<CbcResultDto>> response = new CustomResponse<>();
        try {
            Optional<Grade> gradeOpt = gradeRepository.findById(gradeId);
            Optional<Term> termOpt = termResolver.resolveById(termId);

            if (gradeOpt.isEmpty() || termOpt.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Grade or Term not found");
                return response;
            }

            Grade grade = gradeOpt.get();
            Term term = termOpt.get();
            Year acaYear = Year.of(year);

            List<StudentsScore> allScores = studentsScoreRepository
                    .findByGradeAndTermAndYear(grade, term, acaYear);
            if (allScores.isEmpty()) {
                // Backward-compatible fallback for rows persisted without the year populated.
                allScores = studentsScoreRepository.findByTermAndGrade(term, grade).stream()
                        .filter(s -> s.getYear() != null && s.getYear().equals(acaYear))
                        .toList();
            }

            if (allScores.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("No scores found for this grade/term/year to compute");
                return response;
            }

            // Resolve the framework once for this grade.
            Optional<AssessmentFramework> framework = frameworkResolutionService.resolveForGrade(grade);

            Map<Student, Map<AcademicSubject, List<StudentsScore>>> grouped = allScores.stream()
                    .filter(s -> s.getAcademicSubject() != null)
                    .collect(Collectors.groupingBy(
                            StudentsScore::getStudent,
                            Collectors.groupingBy(StudentsScore::getAcademicSubject)));

            List<CbcResultDto> resultsDtos = new ArrayList<>();
            int computedCount = 0;

            for (var studentEntry : grouped.entrySet()) {
                Student student = studentEntry.getKey();
                for (var subjectEntry : studentEntry.getValue().entrySet()) {
                    AcademicSubject academicSubject = subjectEntry.getKey();
                    List<StudentsScore> scores = subjectEntry.getValue();

                    BigDecimal percentage = normalizedSubjectPercentage(scores);
                    CbcGradeResult gradeResult = upsertResult(student, academicSubject, term, acaYear,
                            percentage, framework.orElse(null));
                    computedCount++;
                    resultsDtos.add(toDto(gradeResult, academicSubject, framework.orElse(null)));
                }
            }

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Computed CBC results for " + computedCount + " student-subject combinations"
                    + framework.map(f -> " using framework " + f.getCode()).orElse(" (no framework configured; used fallback bands)"));
            response.setEntity(resultsDtos);
        } catch (Exception e) {
            log.error("Error computing CBC grades for grade {}: {}", gradeId, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error computing CBC grades: " + e.getMessage());
        }
        return response;
    }

    /**
     * Average the normalized percentages of the scores for one subject. Each score is normalized by
     * its own maximum (rawScore/maximumScore) when available; legacy rows with only examScore are
     * treated as already-percentage values (0–100).
     */
    private BigDecimal normalizedSubjectPercentage(List<StudentsScore> scores) {
        List<BigDecimal> percentages = new ArrayList<>();
        for (StudentsScore sc : scores) {
            BigDecimal raw = sc.effectiveRawScore();
            if (raw == null) {
                continue;
            }
            BigDecimal max = sc.getMaximumScore();
            if (max != null && max.compareTo(BigDecimal.ZERO) > 0) {
                percentages.add(calculationService.normalize(raw, max));
            } else {
                // Legacy examScore is assumed to already be a percentage/mark out of 100.
                percentages.add(raw);
            }
        }
        return calculationService.average(percentages);
    }

    private CbcGradeResult upsertResult(Student student, AcademicSubject academicSubject, Term term,
                                        Year acaYear, BigDecimal percentage, AssessmentFramework framework) {
        CbcGradeResult result = cbcResultRepository
                .findByStudentAndAcademicSubjectAndTermAndYear(student, academicSubject, term, acaYear)
                .orElseGet(() -> {
                    CbcGradeResult r = new CbcGradeResult();
                    r.setStudent(student);
                    r.setAcademicSubject(academicSubject);
                    r.setTerm(term);
                    r.setYear(acaYear);
                    return r;
                });

        result.setNormalizedScore(percentage);
        result.setAverageScore(percentage == null ? null : percentage.doubleValue());
        result.setCalculationVersion(CALC_VERSION);

        Optional<PerformanceLevel> level = (framework != null && percentage != null)
                ? performanceLevelService.resolve(framework, percentage)
                : Optional.empty();

        if (level.isPresent()) {
            PerformanceLevel pl = level.get();
            result.setPerformanceLevel(pl);
            result.setPerformancePoints(pl.getPoints());
            result.setCbcLevel(pl.getBroadLevel() == null ? null : pl.getBroadLevel().getBroadLevel());
            result.setCbcLabel(pl.getLabel());
            result.setAssessmentFrameworkId(framework.getId());
            if (framework.getCurriculumVersion() != null) {
                result.setCurriculumVersionId(framework.getCurriculumVersion().getId());
            }
        } else {
            // Fallback: derive a broad level from generic bands (only when no framework configured).
            BroadPerformanceLevel broad = fallbackBroadLevel(percentage);
            result.setPerformanceLevel(null);
            result.setPerformancePoints(null);
            result.setCbcLevel(broad == null ? null : broad.getBroadLevel());
            result.setCbcLabel(broad == null ? null : broad.getLabel());
            if (framework != null) {
                result.setAssessmentFrameworkId(framework.getId());
            }
        }
        return cbcResultRepository.save(result);
    }

    /**
     * Generic fallback bands used ONLY when no assessment framework is configured. These are broad
     * CBC categories, not any specific KNEC/KICD ranges, and exist so the endpoint still returns a
     * level before curriculum data is seeded. Once a framework exists, configured bands are used.
     */
    private BroadPerformanceLevel fallbackBroadLevel(BigDecimal percentage) {
        if (percentage == null) {
            return null;
        }
        double p = percentage.doubleValue();
        if (p >= 76) return BroadPerformanceLevel.EXCEEDING_EXPECTATION;
        if (p >= 51) return BroadPerformanceLevel.MEETING_EXPECTATION;
        if (p >= 26) return BroadPerformanceLevel.APPROACHING_EXPECTATION;
        return BroadPerformanceLevel.BELOW_EXPECTATION;
    }

    @Override
    public CustomResponse<List<CbcResultDto>> getResultsForStudent(Long studentId, Long termId) {
        CustomResponse<List<CbcResultDto>> response = new CustomResponse<>();
        try {
            Optional<Student> studentOpt = studentRepository.findById(studentId);
            Optional<Term> termOpt = termResolver.resolveById(termId);

            if (studentOpt.isEmpty() || termOpt.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Student or Term not found");
                return response;
            }

            List<CbcGradeResult> results = cbcResultRepository
                    .findByStudentAndTerm(studentOpt.get(), termOpt.get());
            if (results.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("No CBC results found for this student and term");
                return response;
            }

            List<CbcResultDto> dtos = results.stream()
                    .map(r -> toDto(r, r.getAcademicSubject(), null))
                    .collect(Collectors.toList());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Results retrieved successfully");
            response.setEntity(dtos);
        } catch (Exception e) {
            log.error("Error fetching CBC results for student {}: {}", studentId, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error fetching CBC results: " + e.getMessage());
        }
        return response;
    }

    private CbcResultDto toDto(CbcGradeResult r, AcademicSubject subject, AssessmentFramework framework) {
        CbcResultDto dto = new CbcResultDto();
        dto.setId(r.getId());
        if (r.getStudent() != null) {
            dto.setStudentId(r.getStudent().getId());
            dto.setStudentName(r.getStudent().getFirstName() + " " + r.getStudent().getLastName());
        }
        if (subject != null) {
            dto.setSubjectId(subject.getId());
            dto.setSubjectName(subject.getSubjectName());
        }
        dto.setTermName(r.getTerm() == null ? null : r.getTerm().name());
        dto.setYear(r.getYear() == null ? null : r.getYear().getValue());
        dto.setAverageScore(r.getAverageScore());
        dto.setNormalizedScore(r.getNormalizedScore());
        dto.setCbcLevel(r.getCbcLevel());
        dto.setCbcLabel(r.getCbcLabel());
        dto.setPerformancePoints(r.getPerformancePoints());
        dto.setTeacherComment(r.getTeacherComment());
        if (r.getPerformanceLevel() != null) {
            PerformanceLevel pl = r.getPerformanceLevel();
            dto.setPerformanceLevelCode(pl.getCode());
            dto.setPerformanceLevelLabel(pl.getLabel());
            dto.setPerformanceAbbreviation(pl.getAbbreviation());
        }
        if (framework != null) {
            dto.setAssessmentFrameworkCode(framework.getCode());
        }
        return dto;
    }
}
