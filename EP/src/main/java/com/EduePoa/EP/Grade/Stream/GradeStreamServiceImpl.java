package com.EduePoa.EP.Grade.Stream;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.Grade.Stream.Requests.GradeStreamCreateRequest;
import com.EduePoa.EP.Grade.Stream.Requests.GradeStreamDto;
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
    private final AuditService auditService;

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

    private GradeStreamDto toDto(GradeStream stream) {
        Grade grade = stream.getGrade();
        return new GradeStreamDto(
                stream.getId(),
                stream.getName(),
                grade != null ? grade.getId() : null,
                grade != null ? grade.getName() : null
        );
    }
}
