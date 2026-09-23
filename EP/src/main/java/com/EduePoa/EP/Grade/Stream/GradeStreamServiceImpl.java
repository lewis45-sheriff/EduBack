package com.EduePoa.EP.Grade.Stream;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.Grade.Stream.Requests.GradeStreamCreateRequest;
import com.EduePoa.EP.Grade.Stream.Requests.GradeStreamDto;
import com.EduePoa.EP.Staff.Staff;
import com.EduePoa.EP.Staff.StaffRepository;
import com.EduePoa.EP.Staff.Enum.StaffType;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class GradeStreamServiceImpl implements GradeStreamService {
    private final GradeStreamRepository gradeStreamRepository;
    private final GradeRepository gradeRepository;
    private final StaffRepository staffRepository;
    private final AuditService auditService;

    // Note: gradeRepository is also used to ensure a teacher who is a grade-level
    // class teacher is not simultaneously assigned to a stream.

    private static final java.util.Set<StaffType> CLASS_TEACHER_TYPES = java.util.EnumSet.of(
            StaffType.TEACHER, StaffType.HEAD_TEACHER, StaffType.DEPUTY_HEAD_TEACHER);

    @Override
    @Audit(module = "GRADE STREAM MANAGEMENT", action = "CREATE")
    public CustomResponse<?> createStreams(GradeStreamCreateRequest request) {
        CustomResponse<List<GradeStreamDto>> response = new CustomResponse<>();
        List<GradeStreamDto> createdStreams = new ArrayList<>();

        try {
            if (request == null || request.getGradeId() == null) {
                throw new IllegalArgumentException("Grade id is required.");
            }
            if (request.getStreamNames() == null || request.getStreamNames().isEmpty()) {
                throw new IllegalArgumentException("At least one stream name is required.");
            }

            Optional<Grade> gradeOpt = gradeRepository.findById(request.getGradeId());
            if (gradeOpt.isEmpty()) {
                response.setMessage("Grade not found with id: " + request.getGradeId());
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                return response;
            }

            Grade grade = gradeOpt.get();

            for (String rawName : request.getStreamNames()) {
                if (rawName == null || rawName.trim().isEmpty()) {
                    log.warn("Skipping blank stream name for grade '{}'", grade.getName());
                    continue;
                }

                String streamName = rawName.trim();
                streamName = streamName.substring(0, 1).toUpperCase() + streamName.substring(1);

                if (gradeStreamRepository.findByGradeIdAndName(grade.getId(), streamName).isPresent()) {
                    log.warn("Stream '{}' already exists for grade '{}', skipping.", streamName, grade.getName());
                    continue;
                }

                GradeStream stream = new GradeStream();
                stream.setName(streamName);
                stream.setGrade(grade);

                GradeStream saved = gradeStreamRepository.save(stream);
                log.info("Created stream '{}' for grade '{}'", streamName, grade.getName());

                createdStreams.add(toDto(saved));
            }

            if (!createdStreams.isEmpty()) {
                response.setMessage("Grade streams created successfully.");
                response.setStatusCode(HttpStatus.CREATED.value());
                response.setEntity(createdStreams);
                auditService.log("GRADE_STREAM_MANAGEMENT", "Created", String.valueOf(createdStreams.size()),
                        "streams for grade:", grade.getName());
            } else {
                response.setMessage("No new streams were created. All streams already exist.");
                response.setStatusCode(HttpStatus.OK.value());
            }

        } catch (IllegalArgumentException e) {
            log.error("Invalid input: {}", e.getMessage());
            response.setMessage("Error: " + e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            log.error("Unexpected error while creating grade streams: {}", e.getMessage(), e);
            response.setMessage("An unexpected error occurred. Please try again later.");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        return response;
    }

    @Override
    @Audit(module = "GRADE STREAM MANAGEMENT", action = "GET_BY_GRADE")
    public CustomResponse<?> getStreamsByGrade(Long gradeId) {
        CustomResponse<List<GradeStreamDto>> response = new CustomResponse<>();

        try {
            if (gradeId == null) {
                response.setMessage("Grade id is required.");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            List<GradeStreamDto> streams = gradeStreamRepository.findByGradeId(gradeId)
                    .stream()
                    .map(this::toDto)
                    .toList();

            response.setMessage(streams.isEmpty()
                    ? "No streams found for grade id: " + gradeId
                    : "Streams retrieved successfully.");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(streams);

        } catch (Exception e) {
            log.warn("An unexpected error occurred while retrieving streams for grade {}: {}", gradeId, e.getMessage());
            response.setMessage("An unexpected error occurred while retrieving streams.");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        return response;
    }

    @Override
    @Audit(module = "GRADE STREAM MANAGEMENT", action = "GET_ALL")
    public CustomResponse<?> getAllStreams() {
        CustomResponse<List<GradeStreamDto>> response = new CustomResponse<>();

        try {
            List<GradeStreamDto> streams = gradeStreamRepository.findAll()
                    .stream()
                    .map(this::toDto)
                    .toList();

            response.setMessage(streams.isEmpty() ? "No streams found" : "All streams retrieved successfully.");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(streams);

        } catch (Exception e) {
            log.warn("An unexpected error occurred while retrieving all streams: {}", e.getMessage());
            response.setMessage("An unexpected error occurred while retrieving all streams.");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        return response;
    }

    @Override
    @Audit(module = "GRADE STREAM MANAGEMENT", action = "DELETE")
    public CustomResponse<?> delete(Long id) {
        CustomResponse<?> response = new CustomResponse<>();
        try {
            Optional<GradeStream> streamOpt = gradeStreamRepository.findById(id);

            if (streamOpt.isEmpty()) {
                response.setMessage("Stream not found with id: " + id);
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                return response;
            }

            GradeStream stream = streamOpt.get();
            stream.setDeletedFlag('Y');
            gradeStreamRepository.save(stream);

            response.setMessage("Stream deleted successfully (soft delete).");
            response.setStatusCode(HttpStatus.OK.value());
            auditService.log("GRADE_STREAM_MANAGEMENT", "Deleted stream:", stream.getName(),
                    "with ID:", String.valueOf(id));

        } catch (RuntimeException e) {
            log.error("Error during soft delete of stream with id {}: {}", id, e.getMessage(), e);
            response.setMessage("An error occurred while deleting the stream.");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Audit(module = "GRADE STREAM MANAGEMENT", action = "ASSIGN_CLASS_TEACHER")
    public CustomResponse<?> assignClassTeacher(Long streamId, Long staffId) {
        CustomResponse<GradeStreamDto> response = new CustomResponse<>();
        try {
            if (streamId == null || staffId == null) {
                response.setMessage("streamId and staffId are required.");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            Optional<GradeStream> streamOpt = gradeStreamRepository.findById(streamId);
            if (streamOpt.isEmpty()) {
                response.setMessage("Stream not found with id: " + streamId);
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                return response;
            }

            Staff staff = staffRepository.findById(staffId)
                    .filter(s -> s.getDeletedFlag() == 'N')
                    .orElse(null);
            if (staff == null) {
                response.setMessage("Staff member not found with id: " + staffId);
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                return response;
            }

            if (staff.getStaffType() == null || !CLASS_TEACHER_TYPES.contains(staff.getStaffType())) {
                response.setMessage("Only teaching staff (TEACHER, HEAD_TEACHER, DEPUTY_HEAD_TEACHER) "
                        + "can be assigned as a class teacher.");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            // A teacher may be class teacher of only one class at a time — check both
            // other streams and any grade-level assignment.
            Optional<GradeStream> existingAssignment = gradeStreamRepository.findByClassTeacherId(staffId);
            if (existingAssignment.isPresent() && !existingAssignment.get().getId().equals(streamId)) {
                response.setMessage("This teacher is already the class teacher of stream '"
                        + existingAssignment.get().getName() + "'. Unassign them first.");
                response.setStatusCode(HttpStatus.CONFLICT.value());
                return response;
            }
            Optional<com.EduePoa.EP.Grade.Grade> gradeAssignment =
                    gradeRepository.findByClassTeacherId(staffId);
            if (gradeAssignment.isPresent()) {
                response.setMessage("This teacher is already the class teacher of grade '"
                        + gradeAssignment.get().getName() + "'. Unassign them first.");
                response.setStatusCode(HttpStatus.CONFLICT.value());
                return response;
            }

            GradeStream stream = streamOpt.get();
            stream.setClassTeacher(staff);
            GradeStream saved = gradeStreamRepository.save(stream);

            response.setMessage("Class teacher assigned successfully.");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(toDto(saved));
            auditService.log("GRADE_STREAM_MANAGEMENT", "Assigned class teacher staffId:",
                    String.valueOf(staffId), "to stream:", saved.getName());
            log.info("Assigned class teacher staffId={} to streamId={}", staffId, streamId);

        } catch (Exception e) {
            log.error("Error assigning class teacher (streamId={}, staffId={}): {}", streamId, staffId, e.getMessage(), e);
            response.setMessage("An unexpected error occurred while assigning the class teacher.");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Audit(module = "GRADE STREAM MANAGEMENT", action = "REMOVE_CLASS_TEACHER")
    public CustomResponse<?> removeClassTeacher(Long streamId) {
        CustomResponse<GradeStreamDto> response = new CustomResponse<>();
        try {
            if (streamId == null) {
                response.setMessage("streamId is required.");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            Optional<GradeStream> streamOpt = gradeStreamRepository.findById(streamId);
            if (streamOpt.isEmpty()) {
                response.setMessage("Stream not found with id: " + streamId);
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                return response;
            }

            GradeStream stream = streamOpt.get();
            stream.setClassTeacher(null);
            GradeStream saved = gradeStreamRepository.save(stream);

            response.setMessage("Class teacher removed successfully.");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(toDto(saved));
            auditService.log("GRADE_STREAM_MANAGEMENT", "Removed class teacher from stream:",
                    saved.getName(), "with ID:", String.valueOf(streamId));
            log.info("Removed class teacher from streamId={}", streamId);

        } catch (Exception e) {
            log.error("Error removing class teacher (streamId={}): {}", streamId, e.getMessage(), e);
            response.setMessage("An unexpected error occurred while removing the class teacher.");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    private GradeStreamDto toDto(GradeStream stream) {
        Grade grade = stream.getGrade();
        Staff teacher = stream.getClassTeacher();
        return new GradeStreamDto(
                stream.getId(),
                stream.getName(),
                grade != null ? grade.getId() : null,
                grade != null ? grade.getName() : null,
                teacher != null ? teacher.getId() : null,
                teacher != null ? buildFullName(teacher) : null,
                teacher != null ? teacher.getEmployeeNumber() : null
        );
    }

    private String buildFullName(Staff staff) {
        StringBuilder sb = new StringBuilder();
        if (staff.getFirstName() != null) sb.append(staff.getFirstName().trim());
        if (staff.getOtherNames() != null && !staff.getOtherNames().trim().isEmpty()) {
            sb.append(" ").append(staff.getOtherNames().trim());
        }
        if (staff.getLastName() != null) sb.append(" ").append(staff.getLastName().trim());
        return sb.toString().trim();
    }
}
