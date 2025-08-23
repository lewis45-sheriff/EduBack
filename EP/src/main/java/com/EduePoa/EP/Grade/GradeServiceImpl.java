package com.EduePoa.EP.Grade;

import com.EduePoa.EP.Grade.Requests.GradeDto;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@AllArgsConstructor
@Slf4j
public class GradeServiceImpl implements  GradeService{
    private final GradeRepository gradeRepository;

    @Override
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

        } catch (RuntimeException e) {
            log.error("Error during soft delete of grade with id {}: {}", id, e.getMessage(), e);
            response.setMessage("An error occurred while deleting the grade.");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }
        return response;
    }

}
