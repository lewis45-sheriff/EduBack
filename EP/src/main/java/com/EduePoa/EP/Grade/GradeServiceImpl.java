package com.EduePoa.EP.Grade;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Grade.Requests.GradeDto;
import com.EduePoa.EP.Grade.Stream.GradeStream;
import com.EduePoa.EP.Grade.Stream.GradeStreamRepository;
import com.EduePoa.EP.Staff.Staff;
import com.EduePoa.EP.Staff.StaffRepository;
import com.EduePoa.EP.Staff.Enum.StaffType;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@AllArgsConstructor
@Slf4j
public class GradeServiceImpl implements GradeService {
    private final GradeRepository gradeRepository;
    private final GradeStreamRepository gradeStreamRepository;
    private final StaffRepository staffRepository;
    private final AuditService auditService;

    private static final Set<StaffType> CLASS_TEACHER_TYPES = EnumSet.of(
            StaffType.TEACHER, StaffType.HEAD_TEACHER, StaffType.DEPUTY_HEAD_TEACHER);

    @Override
    @Audit(module = "GRADE MANAGEMENT", action = "CREATE")
    public CustomResponse<?> createGrade(String name, Integer start, Integer end) {
        CustomResponse<Set<GradeDto>> response = new CustomResponse<>();
        Set<GradeDto> createdGrades = new HashSet<>();

        log.info("Creating grade(s) with base name '{}', from {} to {}", name, start, end);

        try {
            // Input validation
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Grade name cannot be null or empty.");
            }
            if (start == null || end == null || start > end) {
                throw new IllegalArgumentException("Invalid start or end range provided.");
            }

            // Normalize the grade base name
            name = name.trim();
            name = name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
            log.info("Formatted grade base name to '{}'", name);

            // Loop through the grade range
            for (int i = start; i <= end; i++) {
                String gradeName = name + " " + i;

                // Check if grade already exists
                if (gradeRepository.findByName(gradeName).isEmpty()) {
                    // Create new grade entity
                    Grade grade = new Grade();
                    grade.setGradeNumber(i);
                    grade.setName(gradeName);

                    gradeRepository.save(grade);
                    log.info("Created grade '{}'", gradeName);

                    // Convert to DTO and add to response set
                    GradeDto gradeDto = new GradeDto();
                    gradeDto.setName(gradeName);
                    createdGrades.add(gradeDto);
                } else {
                    log.warn("Grade '{}' already exists, skipping.", gradeName);
                }
            }

            // Build response
            if (!createdGrades.isEmpty()) {
                response.setMessage("Grades created successfully.");
                response.setStatusCode(HttpStatus.CREATED.value());
                response.setEntity(createdGrades);
                auditService.log("GRADE_MANAGEMENT", "Created", String.valueOf(createdGrades.size()),
                        "grades with base name:", name);
            } else {
                response.setMessage("No new grades were created. All grades already exist.");
                response.setStatusCode(HttpStatus.OK.value());
            }

        } catch (IllegalArgumentException e) {
            log.error("Invalid input: {}", e.getMessage());
            response.setMessage("Error: " + e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            log.error("Unexpected error while creating grades: {}", e.getMessage(), e);
            response.setMessage("An unexpected error occurred. Please try again later.");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        return response;
    }

    @Override
    @Audit(module = "GRADE MANAGEMENT", action = "GET_ALL")
    public CustomResponse<?> getAllGrades() {
        CustomResponse<List<Grade>> response = new CustomResponse<>();
        log.info("Fetching all grade/class names");

        try {
            List<Grade> grades = gradeRepository.findAll();

            if (grades.isEmpty()) {
                response.setMessage("No Grades found");
                response.setStatusCode(HttpStatus.OK.value());
                response.setEntity(null);
            } else {
                response.setMessage("All grades retrieved successfully.");
                response.setStatusCode(HttpStatus.OK.value());
                response.setEntity(grades);
                auditService.log("GRADE_MANAGEMENT", "Retrieved", String.valueOf(grades.size()), "grades");
            }
        } catch (Exception e) {
            response.setMessage("An unexpected error occurred while retrieving all grades.");

            // log error message instead of showing it
            log.warn("An unexpected error occurred while retrieving all grades.{}", e.getMessage());

            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        return response;
    }

    @Override
    @Audit(module = "GRADE MANAGEMENT", action = "DELETE")
    public CustomResponse<?> delete(Long id) {
        CustomResponse<?> response = new CustomResponse<>();
        try {
            Optional<Grade> gradeOpt = gradeRepository.findById(id);

            if (gradeOpt.isEmpty()) {
                response.setMessage("Grade not found with id: " + id);
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setEntity(null);
                return response;
            }

            Grade grade = gradeOpt.get();

            // Soft delete: mark as deleted
            grade.setDeletedFlag('Y');
            gradeRepository.save(grade);

            response.setMessage("Grade deleted successfully (soft delete).");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(null);
            auditService.log("GRADE_MANAGEMENT", "Deleted grade:", grade.getName(), "with ID:", String.valueOf(id));

        } catch (RuntimeException e) {
            log.error("Error during soft delete of grade with id {}: {}", id, e.getMessage(), e);
            response.setMessage("An error occurred while deleting the grade.");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    @Audit(module = "GRADE MANAGEMENT", action = "ASSIGN_CLASS_TEACHER")
    public CustomResponse<?> assignClassTeacher(Long gradeId, Long staffId) {
        CustomResponse<GradeDto> response = new CustomResponse<>();
        try {
            if (gradeId == null || staffId == null) {
                response.setMessage("gradeId and staffId are required.");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            Optional<Grade> gradeOpt = gradeRepository.findById(gradeId);
            if (gradeOpt.isEmpty()) {
                response.setMessage("Grade not found with id: " + gradeId);
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                return response;
            }
            Grade grade = gradeOpt.get();

            // A grade-level class teacher only makes sense when the grade has no streams.
            // Streamed grades assign the class teacher per stream instead.
            boolean hasStreams = !gradeStreamRepository.findByGradeId(gradeId).isEmpty();
            if (hasStreams) {
                response.setMessage("Grade '" + grade.getName() + "' has streams. "
                        + "Assign the class teacher to a stream instead.");
                response.setStatusCode(HttpStatus.CONFLICT.value());
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

            // A teacher may be class teacher of only one thing at a time — check both
            // other grades and any stream.
            Optional<Grade> otherGrade = gradeRepository.findByClassTeacherId(staffId);
            if (otherGrade.isPresent() && !otherGrade.get().getId().equals(gradeId)) {
                response.setMessage("This teacher is already the class teacher of grade '"
                        + otherGrade.get().getName() + "'. Unassign them first.");
                response.setStatusCode(HttpStatus.CONFLICT.value());
                return response;
            }
            Optional<GradeStream> streamAssignment = gradeStreamRepository.findByClassTeacherId(staffId);
            if (streamAssignment.isPresent()) {
                response.setMessage("This teacher is already the class teacher of stream '"
                        + streamAssignment.get().getName() + "'. Unassign them first.");
                response.setStatusCode(HttpStatus.CONFLICT.value());
                return response;
            }

            grade.setClassTeacher(staff);
            Grade saved = gradeRepository.save(grade);

            response.setMessage("Class teacher assigned successfully.");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(toDto(saved));
            auditService.log("GRADE_MANAGEMENT", "Assigned class teacher staffId:",
                    String.valueOf(staffId), "to grade:", saved.getName());
            log.info("Assigned class teacher staffId={} to gradeId={}", staffId, gradeId);

        } catch (Exception e) {
            log.error("Error assigning class teacher (gradeId={}, staffId={}): {}", gradeId, staffId, e.getMessage(), e);
            response.setMessage("An unexpected error occurred while assigning the class teacher.");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Audit(module = "GRADE MANAGEMENT", action = "REMOVE_CLASS_TEACHER")
    public CustomResponse<?> removeClassTeacher(Long gradeId) {
        CustomResponse<GradeDto> response = new CustomResponse<>();
        try {
            if (gradeId == null) {
                response.setMessage("gradeId is required.");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            Optional<Grade> gradeOpt = gradeRepository.findById(gradeId);
            if (gradeOpt.isEmpty()) {
                response.setMessage("Grade not found with id: " + gradeId);
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                return response;
            }

            Grade grade = gradeOpt.get();
            grade.setClassTeacher(null);
            Grade saved = gradeRepository.save(grade);

            response.setMessage("Class teacher removed successfully.");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(toDto(saved));
            auditService.log("GRADE_MANAGEMENT", "Removed class teacher from grade:",
                    saved.getName(), "with ID:", String.valueOf(gradeId));
            log.info("Removed class teacher from gradeId={}", gradeId);

        } catch (Exception e) {
            log.error("Error removing class teacher (gradeId={}): {}", gradeId, e.getMessage(), e);
            response.setMessage("An unexpected error occurred while removing the class teacher.");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    private GradeDto toDto(Grade grade) {
        GradeDto dto = new GradeDto();
        dto.setId(grade.getId());
        dto.setName(grade.getName());
        Staff teacher = grade.getClassTeacher();
        if (teacher != null) {
            dto.setClassTeacherId(teacher.getId());
            dto.setClassTeacherName(buildFullName(teacher));
            dto.setClassTeacherEmployeeNumber(teacher.getEmployeeNumber());
        }
        return dto;
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
