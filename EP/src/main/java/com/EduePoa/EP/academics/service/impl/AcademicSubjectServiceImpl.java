package com.EduePoa.EP.academics.service.impl;



import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.dto.request.CreateSubjectRequest;
import com.EduePoa.EP.academics.dto.response.SubjectResponseDto;
import com.EduePoa.EP.academics.entity.AcademicSubject;
import com.EduePoa.EP.academics.entity.ClassSubjectAssignment;
import com.EduePoa.EP.academics.repository.AcademicSubjectRepository;
import com.EduePoa.EP.academics.repository.ClassSubjectAssignmentRepository;
import com.EduePoa.EP.academics.service.AcademicSubjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AcademicSubjectServiceImpl implements AcademicSubjectService {

    private final AcademicSubjectRepository subjectRepository;
    private final GradeRepository gradeRepository;
    private final ClassSubjectAssignmentRepository assignmentRepository;
    private final StudentRepository studentRepository;

    @Override
    public CustomResponse<SubjectResponseDto> createSubject(CreateSubjectRequest request) {
        CustomResponse<SubjectResponseDto> response = new CustomResponse<>();
        try {

            AcademicSubject subject = new AcademicSubject();
            subject.setSubjectName(request.getSubjectName());
            subject.setSubjectCode(request.getSubjectCode());
            subject.setLearningArea(request.getLearningArea());
            subject.setIsCbcCore(request.getIsCbcCore() != null ? request.getIsCbcCore() : false);

            AcademicSubject saved = subjectRepository.save(subject);

            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage("Academic subject created successfully");
            response.setEntity(mapToDto(saved));

        } catch (Exception e) {
            log.error("Error creating academic subject: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error creating subject: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<List<SubjectResponseDto>> getAllSubjects() {
        CustomResponse<List<SubjectResponseDto>> response = new CustomResponse<>();
        try {
            List<AcademicSubject> subjects = subjectRepository.findAll();
            if (subjects.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("No academic subjects found");
                return response;
            }
            List<SubjectResponseDto> dtos = subjects.stream()
                    .map(this::mapToDto)
                    .collect(Collectors.toList());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Subjects retrieved successfully");
            response.setEntity(dtos);

        } catch (Exception e) {
            log.error("Error fetching subjects: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error fetching subjects: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<List<SubjectResponseDto>> getSubjectsByGrade(Long gradeId) {
        CustomResponse<List<SubjectResponseDto>> response = new CustomResponse<>();
        try {
            Optional<Grade> gradeOpt = gradeRepository.findById(gradeId);
            if (gradeOpt.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Grade not found with id: " + gradeId);
                return response;
            }

            // Get subjects via the ClassSubjectAssignment table (current year or all)
            List<ClassSubjectAssignment> assignments = assignmentRepository
                    .findByGradeAndYear(gradeOpt.get(), java.time.Year.now());

            if (assignments.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("No subjects assigned to this grade for the current year");
                return response;
            }

            List<SubjectResponseDto> dtos = assignments.stream()
                    .map(a -> mapToDto(a.getAcademicSubject()))
                    .collect(Collectors.toList());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Subjects for grade retrieved successfully");
            response.setEntity(dtos);

        } catch (Exception e) {
            log.error("Error fetching subjects for grade {}: {}", gradeId, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error fetching subjects for grade: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<Void> deleteSubject(Long subjectId) {
        CustomResponse<Void> response = new CustomResponse<>();
        try {
            if (!subjectRepository.existsById(subjectId)) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Subject not found with id: " + subjectId);
                return response;
            }
            subjectRepository.deleteById(subjectId);
            response.setStatusCode(HttpStatus.NO_CONTENT.value());
            response.setMessage("Subject deleted successfully");

        } catch (Exception e) {
            log.error("Error deleting subject {}: {}", subjectId, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error deleting subject: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getSubjectsByStudent(Long studentId) {
        CustomResponse<List<SubjectResponseDto>> response = new CustomResponse<>();
        try {
            Optional<Student> studentOpt = studentRepository.findById(studentId);
            if (studentOpt.isEmpty()) {
                CustomResponse<Object> errResponse = new CustomResponse<>();
                errResponse.setStatusCode(HttpStatus.NOT_FOUND.value());
                errResponse.setMessage("Student not found with id: " + studentId);
                return errResponse;
            }

            Grade grade = studentOpt.get().getGrade();
            if (grade == null) {
                CustomResponse<Object> errResponse = new CustomResponse<>();
                errResponse.setStatusCode(HttpStatus.NOT_FOUND.value());
                errResponse.setMessage("Student is not assigned to any grade.");
                return errResponse;
            }

            List<ClassSubjectAssignment> assignments = assignmentRepository
                    .findByGradeAndYear(grade, java.time.Year.now());

            if (assignments.isEmpty()) {
                CustomResponse<Object> errResponse = new CustomResponse<>();
                errResponse.setStatusCode(HttpStatus.NOT_FOUND.value());
                errResponse.setMessage("No subjects assigned to this student's grade for the current year");
                return errResponse;
            }

            List<SubjectResponseDto> dtos = assignments.stream()
                    .map(a -> mapToDto(a.getAcademicSubject()))
                    .collect(Collectors.toList());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Subjects for student retrieved successfully");
            response.setEntity(dtos);
            return response;

        } catch (Exception e) {
            log.error("Error fetching subjects for student {}: {}", studentId, e.getMessage(), e);
            CustomResponse<Object> errResponse = new CustomResponse<>();
            errResponse.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            errResponse.setEntity(null);
            errResponse.setMessage("Error fetching subjects for student: " + e.getMessage());
            return errResponse;
        }
    }

    // ── Mapper ────────────────────────────────────────────────────────────────
    private SubjectResponseDto mapToDto(AcademicSubject s) {
        SubjectResponseDto dto = new SubjectResponseDto();
        dto.setId(s.getId());
        dto.setSubjectName(s.getSubjectName());
        dto.setSubjectCode(s.getSubjectCode());
        dto.setLearningArea(s.getLearningArea());
        dto.setIsCbcCore(s.getIsCbcCore());
        return dto;
    }
}
