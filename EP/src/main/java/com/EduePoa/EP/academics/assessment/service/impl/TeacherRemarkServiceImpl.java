package com.EduePoa.EP.academics.assessment.service.impl;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.assessment.dto.RecordTeacherRemarkRequest;
import com.EduePoa.EP.academics.assessment.service.TeacherRemarkService;
import com.EduePoa.EP.academics.common.TermResolver;
import com.EduePoa.EP.academics.entity.CbcGradeResult;
import com.EduePoa.EP.academics.repository.CbcGradeResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persists the class teacher's per-learner, per-term report-card remark.
 *
 * <p>The remark is stored on the learner's {@code cbc_grade_result} rows (one per learning area)
 * so both the Jasper PDF (which reads {@code cgr.teacher_comment}) and the JSON report reflect it.
 * The same remark is written to every learning-area row for the term/year (fan-out) so the single
 * remark shown on the report is consistent regardless of which row the template renders. The CBC
 * compute flow never overwrites {@code teacher_comment}, so a saved remark survives a re-compute.
 *
 * <p>All reads/writes are tenant-scoped automatically via the Hibernate tenant filter on
 * {@link CbcGradeResult}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeacherRemarkServiceImpl implements TeacherRemarkService {

    /** Matches the DB column length on {@link CbcGradeResult#getTeacherComment()}. */
    private static final int MAX_REMARK_LENGTH = 1000;

    private final StudentRepository studentRepository;
    private final CbcGradeResultRepository cbcGradeResultRepository;
    private final TermResolver termResolver;

    @Override
    @Transactional
    public CustomResponse<?> recordRemark(RecordTeacherRemarkRequest request) {
        CustomResponse<Map<String, Object>> response = new CustomResponse<>();
        try {
            if (request == null || request.getStudentId() == null) {
                return badRequest(response, "studentId is required");
            }
            Optional<Term> termOpt = termResolver.resolveById(
                    request.getTermId() == null ? null : request.getTermId().longValue());
            if (termOpt.isEmpty()) {
                return badRequest(response, "A valid termId (1, 2 or 3) is required");
            }
            if (request.getYear() == null) {
                return badRequest(response, "year is required");
            }

            // Normalize: trim, treat blank as a clear (null), and guard the column length.
            String remark = request.getRemark() == null ? null : request.getRemark().trim();
            if (remark != null && remark.isEmpty()) {
                remark = null;
            }
            if (remark != null && remark.length() > MAX_REMARK_LENGTH) {
                return badRequest(response,
                        "Remark is too long (max " + MAX_REMARK_LENGTH + " characters)");
            }

            Student student = studentRepository.findById(request.getStudentId()).orElse(null);
            if (student == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Student not found: " + request.getStudentId());
                return response;
            }

            Term term = termOpt.get();
            Year acaYear = Year.of(request.getYear());

            List<CbcGradeResult> results =
                    cbcGradeResultRepository.findByStudentAndTermAndYear(student, term, acaYear);
            if (results.isEmpty()) {
                // No computed results yet — there is nowhere to attach the remark. Ask the user to
                // compute results first rather than silently succeeding.
                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setMessage("No results have been computed for this learner this term yet, "
                        + "so the remark cannot be saved. Compute the results first, then add the remark.");
                return response;
            }

            for (CbcGradeResult r : results) {
                r.setTeacherComment(remark);
            }
            cbcGradeResultRepository.saveAll(results);

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("studentId", student.getId());
            result.put("term", term.name());
            result.put("year", request.getYear());
            result.put("remark", remark);
            result.put("updatedRows", results.size());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage(remark == null
                    ? "Class teacher's remark cleared"
                    : "Class teacher's remark saved");
            response.setEntity(result);
        } catch (Exception e) {
            log.error("Error recording teacher remark: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error recording teacher remark: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getRemark(Long studentId, Long termId, int year) {
        CustomResponse<Map<String, Object>> response = new CustomResponse<>();
        try {
            Optional<Term> termOpt = termResolver.resolveById(termId);
            if (termOpt.isEmpty()) {
                return badRequest(response, "A valid termId (1, 2 or 3) is required");
            }
            Student student = studentRepository.findById(studentId).orElse(null);
            if (student == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Student not found: " + studentId);
                return response;
            }

            List<CbcGradeResult> results = cbcGradeResultRepository
                    .findByStudentAndTermAndYear(student, termOpt.get(), Year.of(year));

            // The remark is fanned out across rows; surface the first non-blank one.
            String remark = results.stream()
                    .map(CbcGradeResult::getTeacherComment)
                    .filter(c -> c != null && !c.isBlank())
                    .findFirst()
                    .orElse(null);

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("studentId", studentId);
            result.put("term", termOpt.get().name());
            result.put("year", year);
            result.put("remark", remark);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage(remark == null
                    ? "No class teacher's remark has been recorded"
                    : "Class teacher's remark retrieved");
            response.setEntity(result);
        } catch (Exception e) {
            log.error("Error reading teacher remark for student {}: {}", studentId, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error reading teacher remark: " + e.getMessage());
        }
        return response;
    }

    private <T> CustomResponse<T> badRequest(CustomResponse<T> response, String message) {
        response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        response.setMessage(message);
        return response;
    }
}
