package com.EduePoa.EP.academics.service.impl;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.Grade.Stream.GradeStream;
import com.EduePoa.EP.Grade.Stream.GradeStreamRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.common.TermResolver;
import com.EduePoa.EP.academics.dto.request.AssignTeacherToSubjectRequest;
import com.EduePoa.EP.academics.dto.request.BulkAssignTeacherRequest;
import com.EduePoa.EP.academics.dto.response.BulkAssignResultDto;
import com.EduePoa.EP.academics.dto.response.TeacherSubjectAssignmentDto;
import com.EduePoa.EP.academics.entity.AcademicSubject;
import com.EduePoa.EP.academics.entity.TeacherSubjectAssignment;
import com.EduePoa.EP.academics.repository.AcademicSubjectRepository;
import com.EduePoa.EP.academics.repository.TeacherSubjectAssignmentRepository;
import com.EduePoa.EP.academics.service.TeacherAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeacherAssignmentServiceImpl implements TeacherAssignmentService {

    private final TeacherSubjectAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final AcademicSubjectRepository academicSubjectRepository;
    private final GradeRepository gradeRepository;
    private final GradeStreamRepository gradeStreamRepository;
    private final TermResolver termResolver;

    @Override
    @Transactional
    public CustomResponse<TeacherSubjectAssignmentDto> assignTeacher(AssignTeacherToSubjectRequest request) {
        CustomResponse<TeacherSubjectAssignmentDto> response = new CustomResponse<>();
        if (request == null || request.getTeacherId() == null || request.getYear() == null) {
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
            response.setMessage("teacherId and year are required");
            return response;
        }
        try {
            User teacher = userRepository.findById(request.getTeacherId()).orElse(null);
            if (teacher == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Teacher not found");
                return response;
            }
            Year year = Year.of(request.getYear());
            AssignmentOutcome outcome = createAssignment(teacher, request.getAcademicSubjectId(),
                    request.getGradeId(), request.getStreamId(), request.getTermId(), year);
            if (outcome.error != null) {
                response.setStatusCode(outcome.errorStatus.value());
                response.setMessage(outcome.error);
                return response;
            }
            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage("Teacher assigned to subject successfully");
            response.setEntity(toDto(outcome.assignment));
        } catch (Exception e) {
            log.error("Error assigning teacher to subject: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error assigning teacher: " + e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<BulkAssignResultDto> bulkAssign(BulkAssignTeacherRequest request) {
        CustomResponse<BulkAssignResultDto> response = new CustomResponse<>();
        if (request == null || request.getTeacherId() == null || request.getYear() == null
                || request.getAssignments() == null || request.getAssignments().isEmpty()) {
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
            response.setMessage("teacherId, year and at least one assignment are required");
            return response;
        }
        try {
            User teacher = userRepository.findById(request.getTeacherId()).orElse(null);
            if (teacher == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Teacher not found");
                return response;
            }
            Year year = Year.of(request.getYear());
            BulkAssignResultDto result = new BulkAssignResultDto();

            for (BulkAssignTeacherRequest.Item item : request.getAssignments()) {
                AssignmentOutcome outcome = createAssignment(teacher, item.getAcademicSubjectId(),
                        item.getGradeId(), item.getStreamId(), item.getTermId(), year);
                if (outcome.error != null) {
                    result.setSkipped(result.getSkipped() + 1);
                    result.getSkippedReasons().add(outcome.error);
                } else {
                    result.setCreated(result.getCreated() + 1);
                    result.getAssignments().add(toDto(outcome.assignment));
                }
            }

            // 201 if anything was created, 206 if partial, 409 if all were skipped duplicates.
            int status = result.getCreated() > 0
                    ? (result.getSkipped() > 0 ? 206 : HttpStatus.CREATED.value())
                    : HttpStatus.CONFLICT.value();
            response.setStatusCode(status);
            response.setMessage(String.format("Bulk assignment: %d created, %d skipped",
                    result.getCreated(), result.getSkipped()));
            response.setEntity(result);
        } catch (Exception e) {
            log.error("Error in bulk teacher assignment: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error in bulk assignment: " + e.getMessage());
        }
        return response;
    }

    /**
     * Validates one assignment and persists it. Returns an outcome carrying either the saved entity
     * or a human-readable error + status, so both single and bulk callers share the same rules.
     */
    private AssignmentOutcome createAssignment(User teacher, Long subjectId, Long gradeId,
                                               Long streamId, Integer termId, Year year) {
        if (subjectId == null || gradeId == null) {
            return AssignmentOutcome.error(HttpStatus.BAD_REQUEST, "academicSubjectId and gradeId are required");
        }
        AcademicSubject subject = academicSubjectRepository.findById(subjectId).orElse(null);
        Grade grade = gradeRepository.findById(gradeId).orElse(null);
        if (subject == null || grade == null) {
            return AssignmentOutcome.error(HttpStatus.NOT_FOUND,
                    "Subject " + subjectId + " or grade " + gradeId + " not found");
        }

        GradeStream stream = null;
        if (streamId != null) {
            stream = gradeStreamRepository.findById(streamId).orElse(null);
            if (stream == null) {
                return AssignmentOutcome.error(HttpStatus.NOT_FOUND, "Stream " + streamId + " not found");
            }
            if (stream.getGrade() == null || !stream.getGrade().getId().equals(grade.getId())) {
                return AssignmentOutcome.error(HttpStatus.BAD_REQUEST,
                        "Stream " + streamId + " does not belong to grade " + gradeId);
            }
        }

        Term term = null;
        if (termId != null) {
            Optional<Term> termOpt = termResolver.resolveById(termId.longValue());
            if (termOpt.isEmpty()) {
                return AssignmentOutcome.error(HttpStatus.BAD_REQUEST, "Invalid termId " + termId + " (use 1, 2, or 3)");
            }
            term = termOpt.get();
        }

        Long resolvedStreamId = stream == null ? null : stream.getId();
        if (assignmentRepository.existsAssignment(teacher.getId(), grade.getId(), subject.getId(),
                resolvedStreamId, year, term)) {
            return AssignmentOutcome.error(HttpStatus.CONFLICT,
                    "Already assigned: " + subject.getSubjectName() + " / " + grade.getName()
                            + (stream == null ? "" : " " + stream.getName()));
        }

        TeacherSubjectAssignment assignment = new TeacherSubjectAssignment();
        assignment.setTeacher(teacher);
        assignment.setAcademicSubject(subject);
        assignment.setGrade(grade);
        assignment.setGradeStream(stream);
        assignment.setYear(year);
        assignment.setTerm(term);
        assignment.setActive(true);
        return AssignmentOutcome.ok(assignmentRepository.save(assignment));
    }

    /** Internal carrier for a single assignment attempt. */
    private static final class AssignmentOutcome {
        private TeacherSubjectAssignment assignment;
        private String error;
        private HttpStatus errorStatus;

        static AssignmentOutcome ok(TeacherSubjectAssignment a) {
            AssignmentOutcome o = new AssignmentOutcome();
            o.assignment = a;
            return o;
        }

        static AssignmentOutcome error(HttpStatus status, String message) {
            AssignmentOutcome o = new AssignmentOutcome();
            o.errorStatus = status;
            o.error = message;
            return o;
        }
    }

    @Override
    @Transactional
    public CustomResponse<String> unassignTeacher(Long assignmentId) {
        CustomResponse<String> response = new CustomResponse<>();
        Optional<TeacherSubjectAssignment> existing = assignmentRepository.findById(assignmentId);
        if (existing.isEmpty()) {
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            response.setMessage("Assignment not found");
            return response;
        }
        // Soft-deactivate to preserve history rather than hard delete.
        TeacherSubjectAssignment a = existing.get();
        a.setActive(false);
        assignmentRepository.save(a);
        response.setStatusCode(HttpStatus.OK.value());
        response.setMessage("Teacher assignment deactivated");
        return response;
    }

    @Override
    public CustomResponse<List<TeacherSubjectAssignmentDto>> getByTeacher(Long teacherId, int year) {
        return list(assignmentRepository.findByTeacherIdAndYear(teacherId, Year.of(year)));
    }

    @Override
    public CustomResponse<List<TeacherSubjectAssignmentDto>> getByGrade(Long gradeId, int year) {
        return list(assignmentRepository.findByGradeIdAndYear(gradeId, Year.of(year)));
    }

    @Override
    public CustomResponse<List<TeacherSubjectAssignmentDto>> getBySubject(Long subjectId, int year) {
        return list(assignmentRepository.findByAcademicSubjectIdAndYear(subjectId, Year.of(year)));
    }

    private CustomResponse<List<TeacherSubjectAssignmentDto>> list(List<TeacherSubjectAssignment> assignments) {
        CustomResponse<List<TeacherSubjectAssignmentDto>> response = new CustomResponse<>();
        response.setStatusCode(HttpStatus.OK.value());
        response.setMessage("Assignments retrieved");
        response.setEntity(assignments.stream().map(this::toDto).toList());
        return response;
    }

    private TeacherSubjectAssignmentDto toDto(TeacherSubjectAssignment a) {
        TeacherSubjectAssignmentDto dto = new TeacherSubjectAssignmentDto();
        dto.setId(a.getId());
        if (a.getTeacher() != null) {
            dto.setTeacherId(a.getTeacher().getId());
            dto.setTeacherName(safeName(a.getTeacher().getFirstName(), a.getTeacher().getLastName()));
        }
        if (a.getAcademicSubject() != null) {
            dto.setAcademicSubjectId(a.getAcademicSubject().getId());
            dto.setSubjectName(a.getAcademicSubject().getSubjectName());
        }
        if (a.getGrade() != null) {
            dto.setGradeId(a.getGrade().getId());
            dto.setGradeName(a.getGrade().getName());
        }
        if (a.getGradeStream() != null) {
            dto.setStreamId(a.getGradeStream().getId());
            dto.setStreamName(a.getGradeStream().getName());
        }
        dto.setTermName(a.getTerm() == null ? null : a.getTerm().name());
        dto.setYear(a.getYear() == null ? null : a.getYear().getValue());
        dto.setActive(a.getActive());
        return dto;
    }

    private String safeName(String first, String last) {
        String f = first == null ? "" : first;
        String l = last == null ? "" : last;
        return (f + " " + l).trim();
    }
}
