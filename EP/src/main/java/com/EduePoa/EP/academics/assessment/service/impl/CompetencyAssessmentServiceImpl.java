package com.EduePoa.EP.academics.assessment.service.impl;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.assessment.dto.RecordCompetencyEvidenceRequest;
import com.EduePoa.EP.academics.assessment.entity.AssessmentFramework;
import com.EduePoa.EP.academics.assessment.entity.CompetencyEvidence;
import com.EduePoa.EP.academics.assessment.entity.PerformanceLevel;
import com.EduePoa.EP.academics.assessment.repository.CompetencyEvidenceRepository;
import com.EduePoa.EP.academics.assessment.repository.PerformanceLevelRepository;
import com.EduePoa.EP.academics.assessment.service.CompetencyAssessmentService;
import com.EduePoa.EP.academics.assessment.service.FrameworkResolutionService;
import com.EduePoa.EP.academics.common.TermResolver;
import com.EduePoa.EP.academics.curriculum.entity.CoreCompetency;
import com.EduePoa.EP.academics.curriculum.entity.LearningArea;
import com.EduePoa.EP.academics.curriculum.repository.CoreCompetencyRepository;
import com.EduePoa.EP.academics.curriculum.repository.LearningAreaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes and reads learner competency evidence. Levels are resolved from the configurable
 * performance-level bands of the student's assessment framework (by code), never from marks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompetencyAssessmentServiceImpl implements CompetencyAssessmentService {

    private final StudentRepository studentRepository;
    private final CompetencyEvidenceRepository competencyEvidenceRepository;
    private final CoreCompetencyRepository coreCompetencyRepository;
    private final LearningAreaRepository learningAreaRepository;
    private final PerformanceLevelRepository performanceLevelRepository;
    private final FrameworkResolutionService frameworkResolutionService;
    private final UserRepository userRepository;
    private final TermResolver termResolver;

    @Override
    @Transactional
    public CustomResponse<?> recordEvidence(RecordCompetencyEvidenceRequest request) {
        CustomResponse<Map<String, Object>> response = new CustomResponse<>();
        try {
            if (request == null || request.getStudentId() == null) {
                return badRequest(response, "studentId is required");
            }
            if (request.getCompetencies() == null || request.getCompetencies().isEmpty()) {
                return badRequest(response, "At least one competency observation is required");
            }
            Optional<Term> termOpt = termResolver.resolveById(
                    request.getTermId() == null ? null : request.getTermId().longValue());
            if (termOpt.isEmpty()) {
                return badRequest(response, "A valid termId (1, 2 or 3) is required");
            }
            if (request.getYear() == null) {
                return badRequest(response, "year is required");
            }

            Student student = studentRepository.findById(request.getStudentId()).orElse(null);
            if (student == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Student not found: " + request.getStudentId());
                return response;
            }

            Term term = termOpt.get();
            Year acaYear = Year.of(request.getYear());
            User teacher = resolveCurrentUser();

            // Resolve the student's framework once so levels are looked up from the right bands.
            Optional<AssessmentFramework> framework =
                    frameworkResolutionService.resolveForGrade(student.getGrade());

            int saved = 0;
            List<String> errors = new ArrayList<>();

            for (RecordCompetencyEvidenceRequest.CompetencyEntry entry : request.getCompetencies()) {
                if (entry.getCompetencyId() == null) {
                    errors.add("Skipped an entry with no competencyId");
                    continue;
                }
                CoreCompetency competency = coreCompetencyRepository.findById(entry.getCompetencyId()).orElse(null);
                if (competency == null) {
                    errors.add("Competency not found: " + entry.getCompetencyId());
                    continue;
                }

                // Resolve an optional performance level by code within the student's framework.
                PerformanceLevel level = null;
                if (entry.getPerformanceLevelCode() != null && !entry.getPerformanceLevelCode().isBlank()) {
                    if (framework.isPresent()) {
                        level = performanceLevelRepository
                                .findByAssessmentFrameworkAndCode(framework.get(),
                                        entry.getPerformanceLevelCode().trim().toUpperCase())
                                .orElse(null);
                    }
                    if (level == null) {
                        errors.add("Unknown performance level code '" + entry.getPerformanceLevelCode()
                                + "' for competency " + competency.getName() + " (recorded without a level)");
                    }
                }

                LearningArea learningArea = null;
                if (entry.getLearningAreaId() != null) {
                    learningArea = learningAreaRepository.findById(entry.getLearningAreaId()).orElse(null);
                }

                CompetencyEvidence evidence = new CompetencyEvidence();
                evidence.setStudent(student);
                evidence.setCompetency(competency);
                evidence.setGrade(student.getGrade());
                evidence.setTerm(term);
                evidence.setAcademicYear(acaYear);
                evidence.setLearningArea(learningArea);
                evidence.setTeacher(teacher);
                evidence.setPerformanceLevel(level);
                evidence.setComment(entry.getComment());
                evidence.setAssessmentDate(LocalDate.now());

                competencyEvidenceRepository.save(evidence);
                saved++;
            }

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("studentId", student.getId());
            result.put("term", term.name());
            result.put("year", request.getYear());
            result.put("savedCount", saved);
            result.put("errors", errors);

            response.setStatusCode(saved > 0 ? HttpStatus.CREATED.value() : HttpStatus.BAD_REQUEST.value());
            response.setMessage(saved > 0
                    ? "Recorded " + saved + " competency observation(s)"
                    : "No competency observations were recorded");
            response.setEntity(result);
        } catch (Exception e) {
            log.error("Error recording competency evidence: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error recording competency evidence: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getEvidenceForLearner(Long studentId, Long termId, int year) {
        CustomResponse<List<Map<String, Object>>> response = new CustomResponse<>();
        try {
            Optional<Term> termOpt = termResolver.resolveById(termId);
            if (termOpt.isEmpty()) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("A valid termId (1, 2 or 3) is required");
                return response;
            }
            Student student = studentRepository.findById(studentId).orElse(null);
            if (student == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Student not found: " + studentId);
                return response;
            }
            List<CompetencyEvidence> evidence = competencyEvidenceRepository
                    .findByStudentAndTermAndAcademicYear(student, termOpt.get(), Year.of(year));

            List<Map<String, Object>> rows = new ArrayList<>();
            for (CompetencyEvidence ev : evidence) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("id", ev.getId());
                row.put("competencyId", ev.getCompetency() != null ? ev.getCompetency().getId() : null);
                row.put("competencyName", ev.getCompetency() != null ? ev.getCompetency().getName() : null);
                row.put("levelCode", ev.getPerformanceLevel() != null ? ev.getPerformanceLevel().getCode() : null);
                row.put("levelLabel", ev.getPerformanceLevel() != null ? ev.getPerformanceLevel().getLabel() : null);
                row.put("comment", ev.getComment());
                row.put("assessmentDate", ev.getAssessmentDate() != null ? ev.getAssessmentDate().toString() : null);
                rows.add(row);
            }

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Competency evidence retrieved (" + rows.size() + " record(s))");
            response.setEntity(rows);
        } catch (Exception e) {
            log.error("Error reading competency evidence for student {}: {}", studentId, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error reading competency evidence: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> listEvidence(Long gradeId, Long termId, Integer year, Long studentId) {
        CustomResponse<List<Map<String, Object>>> response = new CustomResponse<>();
        try {
            // The admin screen scopes by grade + term + year. Term and year are required so the
            // result set is bounded; grade is the primary filter; studentId is an optional narrow.
            Optional<Term> termOpt = termResolver.resolveById(termId);
            if (termOpt.isEmpty()) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("A valid termId (1, 2 or 3) is required");
                return response;
            }
            if (year == null) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("year is required");
                return response;
            }
            if (gradeId == null && studentId == null) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Either gradeId or studentId is required");
                return response;
            }

            Term term = termOpt.get();
            Year acaYear = Year.of(year);

            List<CompetencyEvidence> evidence;
            if (studentId != null) {
                Student student = studentRepository.findById(studentId).orElse(null);
                if (student == null) {
                    response.setStatusCode(HttpStatus.NOT_FOUND.value());
                    response.setMessage("Student not found: " + studentId);
                    return response;
                }
                evidence = competencyEvidenceRepository
                        .findByStudentAndTermAndAcademicYear(student, term, acaYear);
            } else {
                evidence = competencyEvidenceRepository
                        .findByGrade_IdAndTermAndAcademicYear(gradeId, term, acaYear);
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            for (CompetencyEvidence ev : evidence) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("id", ev.getId());
                row.put("studentId", ev.getStudent() != null ? ev.getStudent().getId() : null);
                row.put("studentName", buildStudentName(ev.getStudent()));
                row.put("competencyId", ev.getCompetency() != null ? ev.getCompetency().getId() : null);
                row.put("competencyName", ev.getCompetency() != null ? ev.getCompetency().getName() : null);
                row.put("level", ev.getPerformanceLevel() != null ? ev.getPerformanceLevel().getPoints() : null);
                row.put("levelLabel", ev.getPerformanceLevel() != null ? ev.getPerformanceLevel().getLabel() : null);
                row.put("evidence", ev.getEvidenceDescription());
                row.put("comment", ev.getComment());
                row.put("assessedOn", ev.getAssessmentDate() != null ? ev.getAssessmentDate().toString() : null);
                rows.add(row);
            }

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Competency evidence retrieved (" + rows.size() + " record(s))");
            response.setEntity(rows);
        } catch (Exception e) {
            log.error("Error listing competency evidence: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error listing competency evidence: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getPerformanceLevelsForLearner(Long studentId) {
        CustomResponse<List<Map<String, Object>>> response = new CustomResponse<>();
        try {
            Student student = studentRepository.findById(studentId).orElse(null);
            if (student == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Student not found: " + studentId);
                return response;
            }

            Optional<AssessmentFramework> framework =
                    frameworkResolutionService.resolveForGrade(student.getGrade());
            if (framework.isEmpty()) {
                // No framework configured for this grade — return an empty list rather than an
                // error, so the UI can fall back gracefully (e.g. a free-text level or a note).
                response.setStatusCode(HttpStatus.OK.value());
                response.setMessage("No assessment framework is configured for this learner's grade");
                response.setEntity(new ArrayList<>());
                return response;
            }

            List<PerformanceLevel> levels = performanceLevelRepository
                    .findByAssessmentFrameworkOrderBySequenceAsc(framework.get());

            List<Map<String, Object>> rows = new ArrayList<>();
            for (PerformanceLevel pl : levels) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("code", pl.getCode());
                row.put("label", pl.getLabel());
                row.put("points", pl.getPoints());
                rows.add(row);
            }

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Performance levels retrieved (" + rows.size() + ")");
            response.setEntity(rows);
        } catch (Exception e) {
            log.error("Error reading performance levels for student {}: {}", studentId, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error reading performance levels: " + e.getMessage());
        }
        return response;
    }

    private String buildStudentName(Student student) {
        if (student == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (student.getFirstName() != null && !student.getFirstName().isBlank()) {
            sb.append(student.getFirstName().trim());
        }
        if (student.getLastName() != null && !student.getLastName().isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(student.getLastName().trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private User resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private <T> CustomResponse<T> badRequest(CustomResponse<T> response, String message) {
        response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        response.setMessage(message);
        return response;
    }
}
